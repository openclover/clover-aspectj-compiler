package com.atlassian.clover.instr.aspectj.util;

import clover.org.apache.commons.lang3.StringUtils;
import com.atlassian.clover.instr.aspectj.util.NameUtils;
import com.atlassian.clover.registry.PersistentAnnotationValue;
import com.atlassian.clover.registry.entities.AnnotationImpl;
import com.atlassian.clover.registry.entities.ArrayAnnotationValue;
import com.atlassian.clover.registry.entities.MethodSignature;
import com.atlassian.clover.registry.entities.Modifier;
import com.atlassian.clover.registry.entities.Modifiers;
import com.atlassian.clover.registry.entities.Parameter;
import com.atlassian.clover.registry.entities.StringifiedAnnotationValue;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Argument;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayInitializer;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Expression;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.MemberValuePair;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeParameter;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.aspectj.org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodSignatureUtil {

    // AJC to Clover constants mapping
    private static final Map<Integer, Integer> AJC_TO_CLOVER = new HashMap<Integer, Integer>();

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

    public static MethodSignature extractConstructorSignature(final ConstructorDeclaration constructorDeclaration) {
        return new MethodSignature(
                new String(constructorDeclaration.selector),
                createGenericTypeParametersFrom(constructorDeclaration.typeParameters),
                null, // no return type
                createParametersFrom(constructorDeclaration.arguments),
                createNamesFrom(constructorDeclaration.thrownExceptions),
                createModifiersAndAnnotationsFrom(constructorDeclaration.modifiers, constructorDeclaration.annotations));
    }

    public static MethodSignature extractMethodSignature(final MethodDeclaration methodDeclaration) {
        return new MethodSignature(
                new String(methodDeclaration.selector),
                createGenericTypeParametersFrom(methodDeclaration.typeParameters),
                methodDeclaration.returnType.toString(),
                createParametersFrom(methodDeclaration.arguments),
                createNamesFrom(methodDeclaration.thrownExceptions),
                createModifiersAndAnnotationsFrom(methodDeclaration.modifiers, methodDeclaration.annotations));
    }

    private static Parameter[] createParametersFrom(final Argument[] arguments) {
        if (arguments != null) {
            final Parameter[] parameters = new Parameter[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                parameters[i] = new Parameter(arguments[i].type.toString(), new String(arguments[i].name));
            }
            return parameters;
        }

        // Clover needs an empty array instead of null
        return new Parameter[0];
    }

    private static String createGenericTypeParametersFrom(final TypeParameter[] types) {
        if (types != null) {
            final String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                names[i] = types[i].toString();
            }
            return "<" + StringUtils.join(names, ",") + ">";
        }

        return null;
    }

    private static String[] createNamesFrom(final TypeReference[] types) {
        if (types != null) {
            final String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                names[i] = NameUtils.qualifiedNameToString(types[i].getTypeName());
            }
            return names;
        }

        return null;
    }

    public static Modifiers createModifiersAndAnnotationsFrom(final int ajcModifiers, final Annotation[] annotations) {
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
                AnnotationImpl cloverAnnotation = new AnnotationImpl(NameUtils.qualifiedNameToString(annotation.type.getTypeName()));
                for (MemberValuePair mvp : annotation.memberValuePairs()) {
                    cloverAnnotation.put(
                            String.valueOf(mvp.name),
                            createAnnotationValueFrom(mvp.value));
                }
                cloverAnnotations.add(cloverAnnotation);
            }

            return Modifiers.createFrom(
                    cloverModifiers,
                    cloverAnnotations.toArray(new AnnotationImpl[cloverAnnotations.size()]));
        }

        return Modifiers.createFrom(cloverModifiers, null);
    }

    /**
     * Convert AspectJ's node with an annotation value into one of Clover's:
     *  - ArrayAnnotationValue  (e.g. "{ String.class, Object.class }")
     *  - StringifiedAnnotationValue (e.g. "something")
     *
     * @param mvpValue Expression from MemberValuePair.value
     * @return PersistentAnnotationValue
     */
    private static PersistentAnnotationValue createAnnotationValueFrom(final Expression mvpValue) {
        if (mvpValue instanceof ArrayInitializer) {
            final ArrayInitializer mvpArray = (ArrayInitializer) mvpValue;
            final ArrayAnnotationValue arrayValue = new ArrayAnnotationValue();
            for (Expression expression : mvpArray.expressions) {
                arrayValue.put(null, createAnnotationValueFrom(expression));
            }
            return arrayValue;
        } else {
            return new StringifiedAnnotationValue(mvpValue.toString());
        }
    }

}
