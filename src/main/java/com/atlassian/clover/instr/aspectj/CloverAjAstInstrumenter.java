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
import com.atlassian.clover.util.ChecksummingReader;
import com.atlassian.clover.util.collections.Pair;
import org.aspectj.ajdt.internal.compiler.ast.DeclareDeclaration;
import org.aspectj.ajdt.internal.compiler.ast.InterTypeMethodDeclaration;
import org.aspectj.ajdt.internal.compiler.ast.PointcutDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Assignment;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Block;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.CaseStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.DoStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ExplicitConstructorCall;
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
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks the entire abstract syntax tree adding Clover instrumentation.
 */
public class CloverAjAstInstrumenter extends ASTVisitor {

    private final LookupEnvironment lookupEnvironment;

    private final InstrumentationSession session;

    private final AjInstrumentationConfig config;

    private CharToLineColMapper lineColMapper;

    private boolean instrumentClass = true;

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
                calculateChecksum(sourceFile)
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

        if (config.isInstrumentAST()) {
            instrumentClass = TypeDeclarationUtils.addCoverageRecorderField(typeDeclaration, lookupEnvironment,
                    config.getInitString(), session.getVersion(), session.getCurrentFileMaxIndex());
        }

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

        if (instrumentClass) {
            // Rewrite the method's code into sth like this
            // try { $CLV_R.inc(index);
            //   ...original code...
            // } finally {
            //    $CLV_R.maybeFlush();
            // }

            // Note: the super() call is stored in ConstructorDeclaration.constructorCall field instead of
            // ConstructorDeclaration.statements, so we don't have to worry about when wrapping statements into the
            // try-catch block - the super() call will be always the first statement

            // $CLV_R.inc(index) for a constructor + original statements (will be instrumented in a Block)
            final int index = methodInfo.getDataIndex();
            final Statement[] statementsPlusOne = insertStatementBefore(
                    createRecorderIncCall(index),
                    constructorDeclaration.statements);

            // encapsulate it in a try-finally block with coverage flushing
            final TryStatement tryBlock = createTryFinallyWithRecorderFlush(statementsPlusOne);

            // swap constructor's code with the new one
            if (config.isInstrumentAST()) {
                constructorDeclaration.statements = new Statement[]{tryBlock};
            }
        }

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
                false,
                getStaticTestName(methodDeclaration)); // TODO we won't see this until issue #1 is fixed

        final boolean ret = super.visit(methodDeclaration, scope);

        if (instrumentClass) {
            // Special case: the InterTypeConstructorDeclaration is handled as a MethodDeclaration and it may have an
            // explicit constructor call declared in the 'statements' field instead of the 'explicitConstructorCall'
            // ensure that we won't wrap this super() in the try-catch block as it must be the first statement
            final boolean hasSuperCall = methodDeclaration.statements != null
                    && methodDeclaration.statements.length > 0
                    && methodDeclaration.statements[0] instanceof ExplicitConstructorCall;
            final int index = methodInfo.getDataIndex();

            if (!hasSuperCall) {
                // Special case: don't instrument pointcuts
                if (!(methodDeclaration instanceof PointcutDeclaration)) {
                    // Rewrite the method's code into sth like this
                    // try { $CLV_R.inc(index);
                    //   ...original code...
                    // } finally {
                    //    $CLV_R.maybeFlush();
                    // }

                    // $CLV_R.inc(index) for a method + original statements (will be instrumented in a Block)
                    final Statement[] statementsPlusOne = insertStatementBefore(
                            createRecorderIncCall(index),
                            methodDeclaration.statements);

                    // encapsulate it in a try-finally block with coverage flushing
                    final TryStatement tryBlock = createTryFinallyWithRecorderFlush(statementsPlusOne);

                    // swap method's code with the new one
                    if (config.isInstrumentAST()) {
                        methodDeclaration.statements = new Statement[]{tryBlock};
                    }
                }
            } else {
                // Rewrite the method's code into this:
                // super(...);
                // try $CLV_R.inc(index);
                //   ...rest of the original code...
                // } finally {
                //    $CLV_R.maybeFlush();
                // }
                final Statement superCall = methodDeclaration.statements[0];
                final Statement[] otherStatements = new Statement[methodDeclaration.statements.length - 1];
                System.arraycopy(methodDeclaration.statements, 1, otherStatements, 0, methodDeclaration.statements.length - 1);

                final TryStatement tryBlock =
                        createTryFinallyWithRecorderFlush(
                                insertStatementBefore(
                                        createRecorderIncCall(index),
                                        otherStatements));

                // swap method's code with the new one
                if (config.isInstrumentAST()) {
                    methodDeclaration.statements = new Statement[]{superCall, tryBlock};
                }
            }
        }

        return ret;
    }

    @Override
    public void endVisit(final MethodDeclaration methodDeclaration, final ClassScope scope) {
        super.endVisit(methodDeclaration, scope);
        exitConstructorOrMethod(methodDeclaration);
    }

    // instrument statements

    /**
     * Variable assignment, e.g. "i = 2"
     */
    @Override
    public void endVisit(final Assignment assignment, final BlockScope scope) {
        // TODO do we need this? instrumentStatement(assignment, scope);
        super.endVisit(assignment, scope);
    }

    /**
     * Case block
     */
    @Override
    public void endVisit(final CaseStatement statement, final BlockScope scope) {
        // TODO we've got labels, not arrays of statements (due to a fall-through ability?)  instrumentStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * Do-while loop
     */
    @Override
    public void endVisit(final DoStatement statement, final BlockScope scope) {
        statement.action = instrumentNonBlockOrEmptyBlockStatement(statement.action, scope);
        super.endVisit(statement, scope);
    }

    /**
     * For statement
     */
    @Override
    public void endVisit(final ForStatement statement, final BlockScope scope) {
        // TODO shall we instrument statement.initializations ?
        // TODO shall we instrument statement.increments ?
        statement.action = instrumentNonBlockOrEmptyBlockStatement(statement.action, scope);
        super.endVisit(statement, scope);
    }

    @Override
    public void endVisit(final Block block, final BlockScope scope) {
        block.statements = instrumentStatements(block.statements, scope);
        super.endVisit(block, scope);
    }

    /**
     * Foreach statement, e.g. "for (line : lines)"
     */
    @Override
    public void endVisit(final ForeachStatement statement, final BlockScope scope) {
        statement.action = instrumentNonBlockOrEmptyBlockStatement(statement.action, scope);
        super.endVisit(statement, scope);
    }

    /**
     * If statement
     */
    @Override
    public void endVisit(final IfStatement statement, final BlockScope scope) {
        statement.thenStatement = instrumentNonBlockOrEmptyBlockStatement(statement.thenStatement, scope);
        if (statement.elseStatement != null) {
            statement.elseStatement = instrumentNonBlockOrEmptyBlockStatement(statement.elseStatement, scope);
        }
        super.endVisit(statement, scope);
    }

    /**
     * Switch statement
     */
    @Override
    public void endVisit(final SwitchStatement switchStatement, final BlockScope scope) {
        // TODO ??? instrumentation is handled by endVisit(CaseStatement) ?
        super.endVisit(switchStatement, scope);
    }

    /**
     * Return statement
     */
    @Override
    public void endVisit(final ReturnStatement assignment, final BlockScope scope) {
        // instrumentation is alrady handled as the return statement is part of other blocks
        super.endVisit(assignment, scope);
    }

    /**
     * Try-catch-finally
     */
    @Override
    public void endVisit(final TryStatement tryStatement, final BlockScope scope) {
        tryStatement.tryBlock = (Block) instrumentNonBlockOrEmptyBlockStatement(tryStatement.tryBlock, scope);
        if (tryStatement.catchBlocks != null) {
            for (int i = 0; i < tryStatement.catchBlocks.length; i++) {
                tryStatement.catchBlocks[i] = (Block) instrumentNonBlockOrEmptyBlockStatement(tryStatement.catchBlocks[i], scope);
            }
        }
        if (tryStatement.finallyBlock != null) {
            tryStatement.finallyBlock = (Block) instrumentNonBlockOrEmptyBlockStatement(tryStatement.finallyBlock, scope);
        }
        super.endVisit(tryStatement, scope);
    }

    /**
     * While loop
     */
    @Override
    public void endVisit(final WhileStatement whileStatement, final BlockScope scope) {
        whileStatement.action = instrumentNonBlockOrEmptyBlockStatement(whileStatement.action, scope);
        super.endVisit(whileStatement, scope);
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

    protected Statement[] instrumentStatements(final Statement[] originalStatements, final BlockScope blockScope) {
        // can't instrument because recorder field is not available
        if (!instrumentClass) {
            return originalStatements;
        }

        // do not instrument statements for METHOD-only level
        if (config.getInstrLevel() == InstrumentationLevel.METHOD) {
            return originalStatements;
        }

        // do not instrument statements related with initialization of class' fields
        if (blockScope instanceof MethodScope
                && ((MethodScope) blockScope).referenceContext instanceof TypeDeclaration) {
            return originalStatements;
        }

        // do not instrument empty blocks, it's handled in other callbacks
        if (originalStatements == null || originalStatements.length == 0) {
            return originalStatements;
        }

        // for every statement in the block, add extra $CLV_R.inc() before it
        final List<Statement> instrStatements = new ArrayList<Statement>(originalStatements.length * 2);
        for (Statement originalStatement : originalStatements) {
            // do not instrument Clover's own instructions
            if (!originalStatement.toString().startsWith("$CLV_R.")) {
                instrStatements.add(createRecorderIncCall(registerStatement(originalStatement)));
            }
            instrStatements.add(originalStatement);
        }

        return config.isInstrumentAST()
                ? instrStatements.toArray(new Statement[instrStatements.size()])
                : originalStatements;
    }

    /**
     * Instrument given statement if one of the following conditions are met:
     *  - statement is not a block (a single statement or an empty statement)
     *  - statement is a block, but is empty
     *
     * If none of the conditions is met, it returns original statement.
     *
     * Note: blocks are normally instrumented by endVisit(Block,BlockScope), which calls instrumentStatements();
     * however, empty blocks does not get an CLV_R.inc() call instruction inside as there's no code context;
     * for this reason we must rewrite non-block statements and empty blocks using instrumentStatement().
     */
    protected Statement instrumentNonBlockOrEmptyBlockStatement(final Statement originalStatement, final BlockScope blockScope) {
        return isNonEmptyBlock(originalStatement)
                ? originalStatement
                : instrumentStatement(originalStatement, blockScope);
    }

    /**
     * Returns true if the statement is a block and it contains at least one statement inside.
     *
     * @param statement statement to be checked
     * @return boolean
     */
    private boolean isNonEmptyBlock(Statement statement) {
        return statement instanceof Block
                && ((Block) statement).statements != null
                && ((Block) statement).statements.length > 0;
    }

    protected Statement instrumentStatement(final Statement originalStatement, final BlockScope blockScope) {
        // can't instrument because recorder field is not available
        if (!instrumentClass) {
            return originalStatement;
        }

        // do not instrument statements for METHOD-only level
        if (config.getInstrLevel() == InstrumentationLevel.METHOD) {
            return originalStatement;
        }

        // do not instrument statements related with initialization of class' fields
        if (blockScope instanceof MethodScope
                && ((MethodScope) blockScope).referenceContext instanceof TypeDeclaration) {
            return originalStatement;
        }

        // do not instrument Clover's own calls
        if (originalStatement.toString().startsWith("$CLV_R.")) {
            return originalStatement;
        }

        // register new statement in a database
        final int index = registerStatement(originalStatement);
        final MessageSend incCall = createRecorderIncCall(index);

        // rewrite node into "{ $CLV_R.inc(index); original_statement; }"
        final Block block = new Block(0);
        block.statements = new Statement[]{incCall, originalStatement};
        return config.isInstrumentAST() ? block : originalStatement;
    }

    protected int registerStatement(final Statement genericStatement) {
        // register new statement in database
        final Pair<Integer, Integer> lineColStart = charIndexToLineCol(genericStatement.sourceStart);
        final Pair<Integer, Integer> lineColEnd = charIndexToLineCol(genericStatement.sourceEnd);
        final FullStatementInfo statementInfo = session.addStatement(
                new ContextSet(),
                new FixedSourceRegion(
                        lineColStart.first, lineColStart.second,
                        lineColEnd.first, lineColEnd.second),
                1,
                LanguageConstruct.Builtin.STATEMENT);

        return statementInfo.getDataIndex();
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
     *
     * @param before   non null
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
        incCall.arguments = new Expression[]{indexLiteral};

        return incCall;
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

    private long calculateChecksum(File file) {
        try {
            if (file.exists()) {
                Reader fileReader;
                if (config.getEncoding() != null) {
                    fileReader = new InputStreamReader(new FileInputStream(file), config.getEncoding());
                } else {
                    fileReader = new FileReader(file);
                }
                final ChecksummingReader chksumReader = new ChecksummingReader(fileReader);
                //noinspection StatementWithEmptyBody
                while (chksumReader.read() != -1) { /*no-op*/ }
                return chksumReader.getChecksum();
            } else {
                return -1L;
            }
        } catch (IOException e) {
            System.out.println("Clover failed to calculate checksum for a file: " + file);
            return -1L;
        }
    }
}
