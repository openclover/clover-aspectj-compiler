package com.atlassian.clover.instr.aspectj.util;

import org.aspectj.org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Expression;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.IntLiteral;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.LongLiteral;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.NullLiteral;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.StringLiteral;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Helper methods to manipulate TypeDeclaration.
 */
public class TypeDeclarationUtils {

    public static final char[] RECORDER_FIELD_NAME = "$CLV_R".toCharArray();

    public static void addCoverageRecorderField(TypeDeclaration type, LookupEnvironment lookupEnvironment,
                                                 String initString, long dbVersion, int fileMaxIndex) {
        // do not add coverage recorder for annotation types
        if ((type.modifiers & ClassFileConstants.AccAnnotation) != 0) {
            return;
        }

        final FieldDeclaration recorderField = new FieldDeclaration(RECORDER_FIELD_NAME, 0, 0);
        recorderField.modifiers = ClassFileConstants.AccPublic | ClassFileConstants.AccStatic | ClassFileConstants.AccFinal;

        final char[][] qualifiedType = NameUtils.stringsToCharArrays("com_atlassian_clover", "CoverageRecorder");
        recorderField.type = new QualifiedTypeReference(qualifiedType, new long[qualifiedType.length]);
        recorderField.bits = type.bits;

        final ReferenceBinding binaryTypeBinding = lookupEnvironment.askForType(qualifiedType);
        final FieldBinding fieldBinding = new FieldBinding(
                RECORDER_FIELD_NAME,
                binaryTypeBinding,
                ClassFileConstants.AccPublic | ClassFileConstants.AccStatic | ClassFileConstants.AccFinal,
                type.binding, // bind to enclosing class,
                null);
        recorderField.binding = fieldBinding;
        recorderField.initialization = createCloverGetRecorderCall(initString, dbVersion, fileMaxIndex);

        // add new field to the class
        addNewFieldDeclaration(type, recorderField);
        addNewFieldBinding(type, fieldBinding);

        // Note: the TypeDeclaration.addClinit() calls needClassInitMethod() which checks for presence of any static
        // fields initializers etc and adds "<clinit>" method if necessary. As it was already called BEFORE we added
        // our recorder field, we must call it again, if <clinit> is not present. Otherwise field won't initialize.
        addClinit(type);
    }

    public static void addNewFieldDeclaration(TypeDeclaration targetType, FieldDeclaration field) {
        final FieldDeclaration[] newFields;
        if (targetType.fields != null) {
            newFields = new FieldDeclaration[targetType.fields.length + 1];
            System.arraycopy(targetType.fields, 0, newFields, 0, targetType.fields.length);
        } else {
            newFields = new FieldDeclaration[1];
        }

        newFields[newFields.length - 1] = field;
        targetType.fields = newFields;
    }

    public static void addNewFieldBinding(TypeDeclaration targetType, FieldBinding field) {
        final FieldBinding[] oldFields = targetType.binding.fields;
        final FieldBinding[] newFields;
        if (oldFields != null) {
            newFields = new FieldBinding[oldFields.length + 1];
            newFields[0] = field;
            System.arraycopy(oldFields, 0, newFields, 1, oldFields.length);
            // field bindings in the class must be sorted because a lookup algorithm uses later a binary search
            Arrays.sort(newFields, new Comparator<FieldBinding>() {
                @Override
                public int compare(FieldBinding left, FieldBinding right) {
                    return String.valueOf(left.name).compareTo(String .valueOf(right.name));
                }
            });
        } else {
            newFields = new FieldBinding[1];
            newFields[0] = field;
        }

        // write fields back
        targetType.binding.fields = newFields;
    }

    /**
     * Create a call:
     * <pre>
     *     com_atlassian_clover.Clover.getRecorder(
     *         config.getInitString(),
     *         session.getVersion(),
     *         0L,
     *         session.getCurrentFileMaxIndex(),
     *         null,
     *         null)
     * </pre>
     *
     * <pre>
     * com.atlassian.clover.Clover.getRecorder(
     *    String initChars, final long dbVersion, final long cfgbits, final int maxNumElements,
     *   CloverProfile[] profiles, final String[] nvpProperties)
     * </pre>
     *
     * @return MessageSend
     */
    private static MessageSend createCloverGetRecorderCall(String initStringStr, long dbVersion, int fileMaxIndex) {
        final MessageSend getRecorderCall = new MessageSend();

        final char[][] cloverType = NameUtils.stringsToCharArrays("com_atlassian_clover", "Clover");
        getRecorderCall.receiver = new QualifiedNameReference(cloverType, new long[cloverType.length], 0, 0);
        getRecorderCall.selector = "getRecorder".toCharArray();

        final char[] initStringChars = initStringStr.toCharArray();
        final char[] dbVersionChars = (dbVersion + "L").toCharArray();
        final char[] cfgBits = "0L".toCharArray();
        final char[] fileMaxIndexChars = Integer.toString(fileMaxIndex).toCharArray();

        getRecorderCall.arguments = new Expression[] {
                new StringLiteral(initStringChars, 0, 0, 0),
                LongLiteral.buildLongLiteral(dbVersionChars, 0, 0),
                LongLiteral.buildLongLiteral(cfgBits, 0, 0),
                IntLiteral.buildIntLiteral(fileMaxIndexChars, 0, 0),
                new NullLiteral(0, 0), // profiles
                new NullLiteral(0, 0), // nvpProperties
        };

        return getRecorderCall;
    }

    private static void addClinit(TypeDeclaration type) {
        if (!isClinitDeclared(type)) {
            type.addClinit();
        }
    }

    private static boolean isClinitDeclared(final TypeDeclaration type) {
        for (AbstractMethodDeclaration method : type.methods) {
            if (method.isClinit()) {
                return true;
            }
        }
        return false;
    }
}
