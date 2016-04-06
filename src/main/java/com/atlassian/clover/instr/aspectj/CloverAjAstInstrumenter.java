package com.atlassian.clover.instr.aspectj;

import com.atlassian.clover.api.instrumentation.InstrumentationSession;
import com.atlassian.clover.api.registry.MethodInfo;
import com.atlassian.clover.cfg.instr.InstrumentationLevel;
import com.atlassian.clover.context.ContextSet;
import com.atlassian.clover.instr.aspectj.text.CharToLineColMapper;
import com.atlassian.clover.instr.aspectj.util.MethodSignatureUtil;
import com.atlassian.clover.instr.aspectj.util.NameUtils;
import com.atlassian.clover.instr.aspectj.util.TypeDeclarationUtils;
import com.atlassian.clover.registry.FixedSourceRegion;
import com.atlassian.clover.registry.entities.FullStatementInfo;
import com.atlassian.clover.registry.entities.MethodSignature;
import com.atlassian.clover.spi.lang.LanguageConstruct;
import com.atlassian.clover.util.collections.Pair;
import org.aspectj.ajdt.internal.compiler.ast.DeclareDeclaration;
import org.aspectj.ajdt.internal.compiler.ast.InterTypeMethodDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Assignment;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Block;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.CaseStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.DoStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Expression;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ForStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ForeachStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.IfStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.IntLiteral;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ReturnStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Statement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.SwitchStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.TryStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.WhileStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.MethodScope;

import java.io.File;

/**
 * Walks the entire abstract syntax tree adding Clover instrumentation.
 */
public class CloverAjAstInstrumenter extends ASTVisitor {

    private final LookupEnvironment lookupEnvironment;

    private final InstrumentationSession session;

    private final AjInstrumentationConfig config;

    private CharToLineColMapper lineColMapper;

    public CloverAjAstInstrumenter(final InstrumentationSession session,
                                   final AjInstrumentationConfig config,
                                   final LookupEnvironment lookupEnvironment) {
        this.session = session;
        this.config = config;
        this.lookupEnvironment = lookupEnvironment;
    }

    // instrument file

    @Override
    public boolean visit(final CompilationUnitDeclaration compilationUnitDeclaration, final CompilationUnitScope scope) {
        // parse current source file and gather information about line endings
        lineColMapper = new CharToLineColMapper(new File(new String(compilationUnitDeclaration.getFileName())));

        final File sourceFile = new File(new String(compilationUnitDeclaration.getFileName()));
        session.enterFile(
                NameUtils.packageNameToString(scope.currentPackageName),
                sourceFile,
                lineColMapper.getLineCount(),
                lineColMapper.getLineCount(),
                sourceFile.lastModified(),
                sourceFile.length(),
                0
        );
        return super.visit(compilationUnitDeclaration, scope);
    }

    @Override
    public void endVisit(final CompilationUnitDeclaration compilationUnitDeclaration, final CompilationUnitScope scope) {
        super.endVisit(compilationUnitDeclaration, scope);
        // parse current source file and gather information about line endings
        session.exitFile();
    }

    // class fields

    @Override
    public boolean visit(final FieldDeclaration fieldDeclaration, final MethodScope scope) {
        return super.visit(fieldDeclaration, scope);
    }

    // instrument top-level class

    @Override
    public boolean visit(final TypeDeclaration typeDeclaration, final CompilationUnitScope scope) {
        final Pair<Integer, Integer> lineCol = charIndexToLineCol(typeDeclaration.declarationSourceStart);
        session.enterClass(
                new String(typeDeclaration.name),
                new FixedSourceRegion(lineCol.first, lineCol.second),
                MethodSignatureUtil.createModifiersAndAnnotationsFrom(typeDeclaration.modifiers, typeDeclaration.annotations),
                false,
                false,
                false);

        TypeDeclarationUtils.addCoverageRecorderField(typeDeclaration, lookupEnvironment,
                config.getInitString(), session.getVersion(), session.getCurrentFileMaxIndex());

        return super.visit(typeDeclaration, scope);
    }

    @Override
    public void endVisit(final TypeDeclaration typeDeclaration, final CompilationUnitScope scope) {
        super.endVisit(typeDeclaration, scope);
        Pair<Integer, Integer> lineCol = charIndexToLineCol(typeDeclaration.declarationSourceEnd);
        session.exitClass(lineCol.first, lineCol.second);
    }

    // instrument constructors

    @Override
    public boolean visit(ConstructorDeclaration constructorDeclaration, ClassScope scope) {
        final MethodInfo methodInfo = enterConstructorOrMethod(
                constructorDeclaration,
                MethodSignatureUtil.extractConstructorSignature(constructorDeclaration),
                false, null);

        final boolean ret = super.visit(constructorDeclaration, scope);

        // Rewrite the method's code into sth like this
        // try { $CLV_R.inc(index);
        //   ...original code...
        // } finally {
        //    $CLV_R.maybeFlush();
        // }

        // Note: the super() call is stored in ConstructorDeclaration.constructorCall field instead of
        // ConstructorDeclaration.statements, so we don't have to worry about when wrapping statements into the
        // try-catch block - the super() call will be always the first statement

        // $CLV_R.inc(index) + original statements
        final int index = methodInfo.getDataIndex();
        final Statement[] statementsPlusOne = insertStatementBefore(
                createRecorderIncCall(index),
                constructorDeclaration.statements);

        // encapsulate it in a try-finally block with coverage flushing
        final TryStatement tryBlock = createTryFinallyWithRecorderFlush(statementsPlusOne);

        // swap constructor's code with the new one
        constructorDeclaration.statements = new Statement[] { tryBlock };

        return ret;
    }

    @Override
    public void endVisit(ConstructorDeclaration constructorDeclaration, ClassScope scope) {
        super.endVisit(constructorDeclaration, scope);
        exitConstructorOrMethod(constructorDeclaration);
    }

    // instrument methods

    @Override
    public boolean visit(final MethodDeclaration methodDeclaration, final ClassScope scope) {
        final MethodInfo methodInfo = enterConstructorOrMethod(
                methodDeclaration,
                MethodSignatureUtil.extractMethodSignature(methodDeclaration),
                treatAsTestMethod(methodDeclaration),
                getStaticTestName(methodDeclaration));

        final boolean ret = super.visit(methodDeclaration, scope);

        // Rewrite the method's code into sth like this
        // try { $CLV_R.inc(index);
        //   ...original code...
        // } finally {
        //    $CLV_R.maybeFlush();
        // }

        // $CLV_R.inc(index) + original statements
        final int index = methodInfo.getDataIndex();
        final Statement[] statementsPlusOne = insertStatementBefore(
                createRecorderIncCall(index),
                methodDeclaration.statements);

        // encapsulate it in a try-finally block with coverage flushing
        final TryStatement tryBlock = createTryFinallyWithRecorderFlush(statementsPlusOne);

        // swap method's code with the new one
        methodDeclaration.statements = new Statement[] { tryBlock };

        return ret;
    }

    @Override
    public void endVisit(final MethodDeclaration methodDeclaration, final ClassScope scope) {
        super.endVisit(methodDeclaration, scope);
        exitConstructorOrMethod(methodDeclaration);
    }

    // instrument statements

    // TODO Expression (?), LabeledStatement (?)

    /**
     * Variable assignment, e.g. "i = 2"
     */
    @Override
    public void endVisit(final Assignment assignment, final BlockScope scope) {
        instrumentStatement(assignment, scope);
        super.endVisit(assignment, scope);
    }

    /**
     * Case block
     */
    @Override
    public void endVisit(final CaseStatement statement, final BlockScope scope) {
        instrumentStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * Do-while loop
     */
    @Override
    public void endVisit(final DoStatement statement, final BlockScope scope) {
        instrumentStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * For statement, e.g. "for (a=0; a < b; a++)"
     */
    @Override
    public void endVisit(final ForStatement statement, final BlockScope scope) {
        instrumentStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * Foreach statement, e.g. "for (line : lines)"
     */
    @Override
    public void endVisit(final ForeachStatement statement, final BlockScope scope) {
        instrumentStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * If statement, e.g. "if (a < b) ..."
     */
    @Override
    public void endVisit(final IfStatement statement, final BlockScope scope) {
        instrumentStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * Local variable declaration, e.g. "Foo f = new Foo();"
     */
    @Override
    public void endVisit(final LocalDeclaration localDeclaration, final BlockScope scope) {
        instrumentStatement(localDeclaration, scope);
        super.endVisit(localDeclaration, scope);
    }

    /**
     * Simple method call, e.g. "foo();"
     */
    @Override
    public void endVisit(final MessageSend messageSend, final BlockScope scope) {
        instrumentStatement(messageSend, scope);
        super.endVisit(messageSend, scope);
    }

    /**
     * Switch statement
     */
    @Override
    public void endVisit(final SwitchStatement assignment, final BlockScope scope) {
        instrumentStatement(assignment, scope);
        super.endVisit(assignment, scope);
    }

    /**
     * Return statement, e.g. "return 2"
     */
    @Override
    public void endVisit(final ReturnStatement assignment, final BlockScope scope) {
        instrumentStatement(assignment, scope);
        super.endVisit(assignment, scope);
    }

    /**
     * Return statement, e.g. "return 2"
     */
    @Override
    public void endVisit(final TryStatement assignment, final BlockScope scope) {
        instrumentStatement(assignment, scope);
        super.endVisit(assignment, scope);
    }

    /**
     * While loop
     */
    @Override
    public void endVisit(final WhileStatement statement, final BlockScope scope) {
        instrumentStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    // helper methods

    protected MethodInfo enterConstructorOrMethod(final AbstractMethodDeclaration constrOrMethod,
                                                  final MethodSignature signature,
                                                  final boolean isTestMethod,
                                                  final String staticTestName) {
        final Pair<Integer, Integer> lineCol = charIndexToLineCol(constrOrMethod.sourceStart);
        return session.enterMethod(
                new ContextSet(),
                new FixedSourceRegion(lineCol.first, lineCol.second),
                signature,
                isTestMethod,
                staticTestName,
                false,
                1,
                LanguageConstruct.Builtin.METHOD);
    }

    protected void exitConstructorOrMethod(final AbstractMethodDeclaration constructorOrMethod) {
        final Pair<Integer, Integer> lineCol = charIndexToLineCol(constructorOrMethod.declarationSourceEnd);
        session.exitMethod(lineCol.first, lineCol.second);
    }

    protected void instrumentStatement(final Statement genericStatement, final BlockScope blockScope) {
        if (config.getInstrLevel() == InstrumentationLevel.METHOD) {
            return;
        }

        // do not instrument statements related with initialization of class' fields
        if (blockScope instanceof MethodScope
                && ((MethodScope) blockScope).referenceContext instanceof TypeDeclaration) {
            return;
        }

        final Pair<Integer, Integer> lineColStart = charIndexToLineCol(genericStatement.sourceStart);
        final Pair<Integer, Integer> lineColEnd = charIndexToLineCol(genericStatement.sourceEnd);
        final FullStatementInfo statementInfo = session.addStatement(
                new ContextSet(),
                new FixedSourceRegion(
                        lineColStart.first, lineColStart.second,
                        lineColEnd.first, lineColEnd.second),
                1,
                LanguageConstruct.Builtin.STATEMENT);
        final int index = statementInfo.getDataIndex();

        // Rewrite node into "$CLV_R.inc(index); original_statement;"

        // $CLV_R.inc(index);
        final IntLiteral indexLiteral = IntLiteral.buildIntLiteral(
                Integer.toString(index).toCharArray(), 0, 0);
        final MessageSend incCall = new MessageSend();
        incCall.receiver = new SingleNameReference(CloverAjCompilerAdapter.RECORDER_FIELD_NAME, 0);
        incCall.selector = "inc".toCharArray();
        incCall.arguments = new Expression[] { indexLiteral };

        // TODO seems that we are visiting statements added by method node rewrite (inc,maybeFlush)
        // TODO shall we rewrite it or rewrite all statements in a method node?
    }

    protected Pair<Integer, Integer> charIndexToLineCol(final int charIndex) {
        // convert char index to line:column
        if (lineColMapper != null) {
            return lineColMapper.getLineColFor(charIndex);
        } else {
            throw new IllegalStateException("lineColMapper has not been initialized!");
        }
    }

    private TryStatement createTryFinallyWithRecorderFlush(Statement[] statementsInTry) {
        // $CLV_R.maybeFlush();
        final MessageSend maybeFlushCall = new MessageSend();
        maybeFlushCall.receiver = new SingleNameReference(CloverAjCompilerAdapter.RECORDER_FIELD_NAME, 0);
        maybeFlushCall.selector = "maybeFlush".toCharArray();

        final TryStatement tryStatement = new TryStatement();
        tryStatement.tryBlock = new Block(1);
        tryStatement.tryBlock.statements = statementsInTry;
        tryStatement.finallyBlock = new Block(0);
        tryStatement.finallyBlock.statements = new Statement[1];
        tryStatement.finallyBlock.statements[0] = maybeFlushCall;

        return tryStatement;
    }

    /**
     * Put one statement before others. Handles null case.
     * @param before non null
     * @param original can be null
     * @return Statement[]
     */
    private Statement[] insertStatementBefore(Statement before, Statement[] original) {
        final Statement[] statementsPlusOne;
        if (original != null) {
            statementsPlusOne = new Statement[original.length + 1];
            statementsPlusOne[0] = before;
            System.arraycopy(original, 0, statementsPlusOne, 1, original.length);
        } else {
            statementsPlusOne = new Statement[1];
            statementsPlusOne[0] = before;
        }
        return statementsPlusOne;
    }

    private MessageSend createRecorderIncCall(int index) {
        // $CLV_R.inc(index);
        final IntLiteral indexLiteral = IntLiteral.buildIntLiteral(
                Integer.toString(index).toCharArray(), 0, 0);
        final MessageSend incCall = new MessageSend();
        incCall.receiver = new SingleNameReference(CloverAjCompilerAdapter.RECORDER_FIELD_NAME, 0);
        incCall.selector = "inc".toCharArray();
        incCall.arguments = new Expression[] { indexLiteral };

        return incCall;
    }

    /**
     * A hack to display a static test name instead of the AJC-generated method name
     */
    private boolean treatAsTestMethod(MethodDeclaration methodDeclaration) {
        return methodDeclaration instanceof DeclareDeclaration
                || methodDeclaration instanceof InterTypeMethodDeclaration;
    }

    /**
     * Extract a friendly name for the AJC-generated method.
     */
    private String getStaticTestName(MethodDeclaration methodDeclaration) {
        if (methodDeclaration instanceof DeclareDeclaration) {
            return ((DeclareDeclaration) methodDeclaration).declareDecl.toString();
        } else if (methodDeclaration instanceof InterTypeMethodDeclaration) {
            return ((InterTypeMethodDeclaration) methodDeclaration).getSignature().toString();
        } else {
            return null;
        }
    }

}
