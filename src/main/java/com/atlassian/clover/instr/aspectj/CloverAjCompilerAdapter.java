package com.atlassian.clover.instr.aspectj;

import com.atlassian.clover.api.instrumentation.InstrumentationSession;
import com_atlassian_clover.Clover;
import org.aspectj.ajdt.internal.compiler.ICompilerAdapter;
import org.aspectj.org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.aspectj.org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.aspectj.org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.aspectj.org.eclipse.jdt.internal.compiler.parser.Parser;
import org.aspectj.org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.aspectj.org.eclipse.jdt.internal.compiler.problem.ProblemReporter;


public class CloverAjCompilerAdapter implements ICompilerAdapter {

    public static final char[] RECORDER_FIELD_NAME = "$CLV_R".toCharArray();
    private final ICompilerAdapter originalAdapter;
    private final InstrumentationSession session;
    private final String initString;
    private final LookupEnvironment lookupEnvironment;

    public CloverAjCompilerAdapter(ICompilerAdapter originalAdapter, InstrumentationSession session, String initString,
                                   LookupEnvironment lookupEnvironment) {
        this.originalAdapter = originalAdapter;
        this.session = session;
        this.initString = initString;
        this.lookupEnvironment = lookupEnvironment;
    }

    @Override
    public void afterAnalysing(CompilationUnitDeclaration unit) {
        originalAdapter.afterAnalysing(unit);
    }

    @Override
    public void afterCompiling(CompilationUnitDeclaration[] units) {
        originalAdapter.afterCompiling(units);
    }

    @Override
    public void afterDietParsing(CompilationUnitDeclaration[] units) {
        originalAdapter.afterDietParsing(units);
    }

    @Override
    public void afterGenerating(CompilationUnitDeclaration unit) {
        originalAdapter.afterGenerating(unit);
    }

    @Override
    public void afterProcessing(CompilationUnitDeclaration unit, int unitIndex) {
        originalAdapter.afterProcessing(unit, unitIndex);
    }

    @Override
    public void afterResolving(CompilationUnitDeclaration unit) {
        originalAdapter.afterResolving(unit);
    }

    @Override
    public void beforeAnalysing(CompilationUnitDeclaration unit) {
        originalAdapter.beforeAnalysing(unit);
    }

    @Override
    public void beforeCompiling(ICompilationUnit[] sourceUnits) {
        originalAdapter.beforeCompiling(sourceUnits);
    }

    @Override
    public void beforeGenerating(CompilationUnitDeclaration unit) {
        originalAdapter.beforeGenerating(unit);
    }

    @Override
    public void beforeProcessing(CompilationUnitDeclaration unit) {
        originalAdapter.beforeProcessing(unit);
    }

    @Override
    public void beforeResolving(CompilationUnitDeclaration unit) {
        unit.traverse(new CloverAjAstInstrumenter(session), unit.scope);
        addCoverageRecorderField(unit);
        originalAdapter.beforeResolving(unit);
    }

    private void addCoverageRecorderField(CompilationUnitDeclaration unit) {
        for (TypeDeclaration type : unit.types) {
            final FieldDeclaration[] newFields;
            if (type.fields != null) {
                newFields = new FieldDeclaration[type.fields.length + 1];
                System.arraycopy(type.fields, 0, newFields, 0, type.fields.length);
            } else {
                newFields = new FieldDeclaration[1];
            }

            FieldDeclaration recorderField = new FieldDeclaration(RECORDER_FIELD_NAME, 0, 0);
            recorderField.modifiers = ClassFileConstants.AccPublic | ClassFileConstants.AccStatic | ClassFileConstants.AccFinal;

            char[][] qualifiedType = new char[2][];
            qualifiedType[0] = "com_atlassian_clover".toCharArray();
            qualifiedType[1] = "CoverageRecorder".toCharArray();
            recorderField.type = new QualifiedTypeReference(qualifiedType, new long[2]);

            final ReferenceBinding binaryTypeBinding = lookupEnvironment.askForType(qualifiedType);
            recorderField.binding = new FieldBinding(
                    RECORDER_FIELD_NAME,
                    binaryTypeBinding,
                    ClassFileConstants.AccPublic | ClassFileConstants.AccStatic | ClassFileConstants.AccFinal,
                    type.binding, // bind to enclosing class,
                    null);

//            recorderField.type.resolveType(type.scope);
            recorderField.resolve(type.staticInitializerScope);

            // com.atlassian.clover.Clover.getRecorder(
            //   String initChars, final long dbVersion, final long cfgbits, final int maxNumElements,
            //   CloverProfile[] profiles, final String[] nvpProperties)
            String initializationSource = Clover.class.getName() + ".getRecorder(\""
                    + initString + "\", "
                    + session.getVersion() + "L, "
                    + "0L,"
                    + session.getCurrentFileMaxIndex() + ","
                    + "null, null)";
            // $CLV_R = Clover.getRecorder()
            ProblemReporter reporter = new ProblemReporter(
                    DefaultErrorHandlingPolicies.proceedWithAllProblems(),
                    new CompilerOptions(),
                    new DefaultProblemFactory());
            new Parser(reporter, true).parse(recorderField, type, unit, initializationSource.toCharArray());

            // add new field
            newFields[newFields.length - 1] = recorderField;
            type.fields = newFields;
        }
    }
}
