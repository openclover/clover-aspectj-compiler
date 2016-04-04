package com.atlassian.clover.instr.aspectj;

import com.atlassian.clover.api.instrumentation.InstrumentationSession;
import com_atlassian_clover.Clover;
import org.aspectj.ajdt.internal.compiler.ICompilerAdapter;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.aspectj.org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.aspectj.org.eclipse.jdt.internal.compiler.parser.Parser;

import java.lang.reflect.Field;


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
        for (CompilationUnitDeclaration unit : units) {
            addCoverageRecorderField(unit);
        }
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
        originalAdapter.beforeResolving(unit);
    }

    private void addCoverageRecorderField(CompilationUnitDeclaration unit) {
        for (TypeDeclaration type : unit.types) {
            // do not add coverage recorder for annotation types
            if ((type.modifiers & ClassFileConstants.AccAnnotation) != 0) {
                continue;
            }

            final FieldDeclaration[] newFields;
            if (type.fields != null) {
                newFields = new FieldDeclaration[type.fields.length + 1];
                System.arraycopy(type.fields, 0, newFields, 0, type.fields.length);
            } else {
                newFields = new FieldDeclaration[1];
            }

            final FieldDeclaration recorderField = new FieldDeclaration(RECORDER_FIELD_NAME, 0, 0);
            recorderField.modifiers = ClassFileConstants.AccPublic | ClassFileConstants.AccStatic | ClassFileConstants.AccFinal;

            char[][] qualifiedType = new char[2][];
            qualifiedType[0] = "com_atlassian_clover".toCharArray();
            qualifiedType[1] = "CoverageRecorder".toCharArray();
            recorderField.type = new QualifiedTypeReference(qualifiedType, new long[2]);
            recorderField.bits = type.bits;

            final ReferenceBinding binaryTypeBinding = lookupEnvironment.askForType(qualifiedType);
            FieldBinding fieldBinding = new FieldBinding(
                    RECORDER_FIELD_NAME,
                    binaryTypeBinding,
                    ClassFileConstants.AccPublic | ClassFileConstants.AccStatic | ClassFileConstants.AccFinal,
                    type.binding, // bind to enclosing class,
                    null);
            recorderField.binding = fieldBinding;
            type.binding.addField(fieldBinding);

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
            getParser().parse(recorderField, type, unit, initializationSource.toCharArray());

            // add new field
            newFields[newFields.length - 1] = recorderField;
            type.fields = newFields;

            // Note: the TypeDeclaration.addClinit() calls needClassInitMethod() which checks for presence of any static
            // fields initializers etc and adds "<clinit>" method if necessary. As it was already called BEFORE we added
            // our recorder field, we must call it again, if <clinit> is not present. Otherwise field won't initialize.
            if (!isClinitDeclared(type)) {
                type.addClinit();
            }
        }
    }

    private boolean isClinitDeclared(final TypeDeclaration type) {
        for (AbstractMethodDeclaration method : type.methods) {
            if (method.isClinit()) {
                return true;
            }
        }
        return false;
    }

    private Parser getParser() {
        try {
            final Field compilerField = originalAdapter.getClass().getDeclaredField("compiler");
            compilerField.setAccessible(true);
            org.aspectj.org.eclipse.jdt.internal.compiler.Compiler compiler =
                    (org.aspectj.org.eclipse.jdt.internal.compiler.Compiler) compilerField.get(originalAdapter);
            return compiler.parser; //org.aspectj.org.eclipse.jdt.internal.compiler.parser.Parser
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
