package com.atlassian.clover.instr.aspectj;

import clover.org.apache.commons.lang3.StringUtils;
import com.atlassian.clover.api.instrumentation.InstrumentationSession;
import com.atlassian.clover.api.registry.MethodInfo;
import com.atlassian.clover.api.registry.PackageInfo;
import com.atlassian.clover.context.ContextSet;
import com.atlassian.clover.registry.FixedSourceRegion;
import com.atlassian.clover.registry.entities.AnnotationImpl;
import com.atlassian.clover.registry.entities.FullStatementInfo;
import com.atlassian.clover.registry.entities.MethodSignature;
import com.atlassian.clover.registry.entities.Modifier;
import com.atlassian.clover.registry.entities.Modifiers;
import com.atlassian.clover.registry.entities.Parameter;
import com.atlassian.clover.registry.entities.StringifiedAnnotationValue;
import com.atlassian.clover.spi.lang.LanguageConstruct;
import com.atlassian.clover.util.collections.Pair;
import org.aspectj.ajdt.internal.compiler.ast.DeclareDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Assignment;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.CaseStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.DoStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ForStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ForeachStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.IfStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.MemberValuePair;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ReturnStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Statement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.SwitchStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.TryStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeParameter;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.WhileStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.MethodScope;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class CloverAjAstInstrumenter extends ASTVisitor {

    private final InstrumentationSession session;

    private CharToLineColMapper lineColMapper;

    public CloverAjAstInstrumenter(InstrumentationSession session) {
        this.session = session;
    }

    // instrument file

    @Override
    public boolean visit(CompilationUnitDeclaration compilationUnitDeclaration, CompilationUnitScope scope) {
        // parse current source file and gather information about line endings
        lineColMapper = new CharToLineColMapper(new File(new String(compilationUnitDeclaration.getFileName())));

        final File sourceFile = new File(new String(compilationUnitDeclaration.getFileName()));
        session.enterFile(
                packageNameToString(scope.currentPackageName),
                sourceFile,
                lineColMapper.getLineCount(),
                lineColMapper.getLineCount(),
                sourceFile.lastModified(),
                sourceFile.length(),
                0
        );
        return super.visit(compilationUnitDeclaration, scope);
    }

    private String packageNameToString(char[][] currentPackageName) {
        String name = "";
        for (int i = 0; i < currentPackageName.length; i++) {
            name += String.valueOf(currentPackageName[i]);
            if (i < currentPackageName.length - 1) {
                name += ".";
            }
        }
        return name.isEmpty() ? PackageInfo.DEFAULT_PACKAGE_NAME : name;
    }

    @Override
    public boolean visit(FieldDeclaration fieldDeclaration, MethodScope scope) {
        return super.visit(fieldDeclaration, scope);
    }

    @Override
    public void endVisit(CompilationUnitDeclaration compilationUnitDeclaration, CompilationUnitScope scope) {
        super.endVisit(compilationUnitDeclaration, scope);
        // parse current source file and gather information about line endings
        session.exitFile();
    }

    // instrument top-level class

    @Override
    public boolean visit(TypeDeclaration typeDeclaration, CompilationUnitScope scope) {
        final Pair<Integer, Integer> lineCol = charIndexToLineCol(typeDeclaration.declarationSourceStart);
        session.enterClass(
                new String(typeDeclaration.name),
                new FixedSourceRegion(lineCol.first, lineCol.second),
                createFrom(typeDeclaration.modifiers, typeDeclaration.annotations),
                false,
                false,
                false);
        return super.visit(typeDeclaration, scope);
    }

    // AJC to Clover constants mapping
    static final Map<Integer, Integer> AJC_TO_CLOVER = new HashMap<Integer, Integer>();

    static {
        AJC_TO_CLOVER.put(ClassFileConstants.AccPublic, Modifier.PUBLIC);
        AJC_TO_CLOVER.put(ClassFileConstants.AccProtected, Modifier.PROTECTED);
        // no ClassFileConstants.AccPackage
        AJC_TO_CLOVER.put(ClassFileConstants.AccPrivate, Modifier.PRIVATE);
        AJC_TO_CLOVER.put(ClassFileConstants.AccAbstract, Modifier.ABSTRACT);
        AJC_TO_CLOVER.put(ClassFileConstants.AccStatic, Modifier.STATIC);
        AJC_TO_CLOVER.put(ClassFileConstants.AccFinal, Modifier.FINAL);
        AJC_TO_CLOVER.put(ClassFileConstants.AccInterface, Modifier.INTERFACE);
        AJC_TO_CLOVER.put(ClassFileConstants.AccNative, Modifier.NATIVE);
        AJC_TO_CLOVER.put(ClassFileConstants.AccSynchronized, Modifier.SYNCHRONIZED);
        AJC_TO_CLOVER.put(ClassFileConstants.AccTransient, Modifier.TRANSIENT);
        AJC_TO_CLOVER.put(ClassFileConstants.AccVolatile, Modifier.VOLATILE);
        // note: Modifier.DEFAULT is an extra Clover one to mark default methods in interfaces (JDK8)
    }

    private Modifiers createFrom(int ajcModifiers, Annotation[] annotations) {
        // convert from AJC to Clover ones
        int cloverModifiers = 0;
        for (Integer ajcModifier : AJC_TO_CLOVER.keySet()) {
            if ( (ajcModifiers & ajcModifier) != 0) {
                cloverModifiers |= AJC_TO_CLOVER.get(ajcModifier);
            }
        }

        // convert from AJC to Clover ones
        final List<AnnotationImpl> cloverAnnotations = new ArrayList<AnnotationImpl>();
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                AnnotationImpl cloverAnnotation = new AnnotationImpl(packageNameToString(annotation.type.getTypeName()));
                for (MemberValuePair mvp : annotation.memberValuePairs()) {
                    // TODO handle also ArrayAnnotationValue; currently we store arrays as simple strings, e.g. "{ 1, 2, 3 }"
                    cloverAnnotation.put(
                            String.valueOf(mvp.name),
                            new StringifiedAnnotationValue(mvp.value.toString()));
                }
                cloverAnnotations.add(cloverAnnotation);
            }

            return Modifiers.createFrom(
                    cloverModifiers,
                    cloverAnnotations.toArray(new AnnotationImpl[cloverAnnotations.size()]));
        }

        return Modifiers.createFrom(cloverModifiers, null);
    }

    @Override
    public void endVisit(TypeDeclaration typeDeclaration, CompilationUnitScope scope) {
        super.endVisit(typeDeclaration, scope);
        Pair<Integer, Integer> lineCol = charIndexToLineCol(typeDeclaration.declarationSourceEnd);
        session.exitClass(lineCol.first, lineCol.second);
    }

    // instrument methods

    @Override
    public boolean visit(MethodDeclaration methodDeclaration, ClassScope scope) {
        final Pair<Integer, Integer> lineCol = charIndexToLineCol(methodDeclaration.declarationSourceStart);
        final MethodInfo methodInfo = session.enterMethod(
                new ContextSet(),
                new FixedSourceRegion(lineCol.first, lineCol.second),
                extractMethodSignature(methodDeclaration),
                methodDeclaration instanceof DeclareDeclaration,  // hack: 'declare' treat as test method to display a friendly name
                methodDeclaration instanceof DeclareDeclaration
                        ? ((DeclareDeclaration) methodDeclaration).declareDecl.toString() // hack: use static test name for 'declare'
                        : null,  // no static test name
                false,
                1,
                LanguageConstruct.Builtin.METHOD);
        int index = methodInfo.getDataIndex();

        // TODO REWRITE THE NODE

        return super.visit(methodDeclaration, scope);
    }

    @Override
    public void endVisit(MethodDeclaration methodDeclaration, ClassScope scope) {
        super.endVisit(methodDeclaration, scope);
        Pair<Integer, Integer> lineCol = charIndexToLineCol(methodDeclaration.declarationSourceEnd);
        session.exitMethod(lineCol.first, lineCol.second);
    }

    // instrument statements

    // TODO Expression (?), LabeledStatement (?)

    /**
     * Variable assignment, e.g. "i = 2"
     */
    @Override
    public void endVisit(Assignment assignment, BlockScope scope) {
        endVisitStatement(assignment, scope);
        super.endVisit(assignment, scope);
    }

    /**
     * Case block
     */
    @Override
    public void endVisit(CaseStatement statement, BlockScope scope) {
        endVisitStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * Do-while loop
     */
    @Override
    public void endVisit(DoStatement statement, BlockScope scope) {
        endVisitStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * For statement, e.g. "for (a=0; a < b; a++)"
     */
    @Override
    public void endVisit(ForStatement statement, BlockScope scope) {
        endVisitStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * Foreach statement, e.g. "for (line : lines)"
     */
    @Override
    public void endVisit(ForeachStatement statement, BlockScope scope) {
        endVisitStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * If statement, e.g. "if (a < b) ..."
     */
    @Override
    public void endVisit(IfStatement statement, BlockScope scope) {
        endVisitStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * Local variable declaration, e.g. "Foo f = new Foo();"
     */
    @Override
    public void endVisit(LocalDeclaration localDeclaration, BlockScope scope) {
        endVisitStatement(localDeclaration, scope);
        super.endVisit(localDeclaration, scope);
    }

    /**
     * Simple method call, e.g. "foo();"
     */
    @Override
    public void endVisit(MessageSend messageSend, BlockScope scope) {
        endVisitStatement(messageSend, scope);
        super.endVisit(messageSend, scope);
    }

    /**
     * Switch statement
     */
    @Override
    public void endVisit(SwitchStatement assignment, BlockScope scope) {
        endVisitStatement(assignment, scope);
        super.endVisit(assignment, scope);
    }

    /**
     * Return statement, e.g. "return 2"
     */
    @Override
    public void endVisit(ReturnStatement assignment, BlockScope scope) {
        endVisitStatement(assignment, scope);
        super.endVisit(assignment, scope);
    }

    /**
     * Return statement, e.g. "return 2"
     */
    @Override
    public void endVisit(TryStatement assignment, BlockScope scope) {
        endVisitStatement(assignment, scope);
        super.endVisit(assignment, scope);
    }

    /**
     * While loop
     */
    @Override
    public void endVisit(WhileStatement statement, BlockScope scope) {
        endVisitStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    // helper methods

    protected void endVisitStatement(Statement genericStatement, BlockScope blockScope) {
        Pair<Integer, Integer> lineColStart = charIndexToLineCol(genericStatement.sourceStart);
        Pair<Integer, Integer> lineColEnd = charIndexToLineCol(genericStatement.sourceEnd);
        FullStatementInfo statementInfo = session.addStatement(
                new ContextSet(),
                new FixedSourceRegion(
                        lineColStart.first, lineColStart.second,
                        lineColEnd.first, lineColEnd.second),
                1,
                LanguageConstruct.Builtin.STATEMENT);
        int index = statementInfo.getDataIndex();

        // TODO REWRITE NODE

    }

    protected MethodSignature extractMethodSignature(MethodDeclaration methodDeclaration) {
        // TODO create a full method signature (annotations, modifiers etc)
        return new MethodSignature(
                new String(methodDeclaration.selector),
                createTypeParametersFrom(methodDeclaration.typeParameters),
                methodDeclaration.returnType.toString(),
                new Parameter[0],
                createNamesFrom(methodDeclaration.thrownExceptions),
                createFrom(methodDeclaration.modifiers, methodDeclaration.annotations));

    }

    private String[] createNamesFrom(TypeReference[] types) {
        if (types != null) {
            final String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                names[i] = packageNameToString(types[i].getTypeName());
            }
            return names;
        }

        return null;
    }

    private String createTypeParametersFrom(TypeParameter[] types) {
        if (types != null) {
            final String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                names[i] = types[i].toString();
            }
            return "<" + StringUtils.join(names, ",") + ">";
        }

        return null;
    }


    protected Pair<Integer, Integer> charIndexToLineCol(int charIndex) {
        // convert char index to line:column
        if (lineColMapper != null) {
            return lineColMapper.getLineColFor(charIndex);
        } else {
            throw new IllegalStateException("lineColMapper has not been initialized!");
        }
    }
}
