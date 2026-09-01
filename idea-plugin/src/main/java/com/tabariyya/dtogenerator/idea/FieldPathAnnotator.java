package com.tabariyya.dtogenerator.idea;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.tabariyya.dtogenerator.fields.FieldConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shows every {@code @FieldPath} violation in the editor. Kept deliberately parallel to
 * {@code FieldPathValidator}: same checks, same order, same strings out of {@link FieldConstants},
 * differing only in that this reads PSI where that reads javac's element model.
 */
public class FieldPathAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        switch (element) {
            case PsiNameValuePair pair -> annotateArgument(pair, holder);
            case PsiMethod method -> annotateDeclaration(method, holder);
            default -> {}
        }
    }

    private static void annotateArgument(PsiNameValuePair pair, AnnotationHolder holder) {
        PsiAnnotation fieldPath = fieldPathOn(pair);
        if (fieldPath == null) {
            return;
        }
        String expectedOwner = expectedOwner(fieldPath, pair, holder);
        if (expectedOwner == null) {
            return;
        }
        for (PsiAnnotationMemberValue value : valuesOf(pair)) {
            String problem = problemWith(value, expectedOwner);
            if (problem != null) {
                report(value, problem, holder);
            }
        }
    }

    private static void annotateDeclaration(PsiMethod method, AnnotationHolder holder) {
        PsiAnnotation fieldPath = method.getAnnotation(FieldConstants.FIELD_PATH_ANNOTATION);
        if (fieldPath == null) {
            return;
        }
        PsiClass owner = method.getContainingClass();
        if (owner != null && !owner.isAnnotationType()) {
            report(fieldPath, FieldConstants.notAnAnnotationMemberMessage(), holder);
        }
        if (takesReturnType(fieldPath) && !fixedOwner(fieldPath).isEmpty()) {
            report(fieldPath, FieldConstants.conflictingOwnerMessage(), holder);
        }
        if (method.getReturnType() != null) {
            requireStringType(fieldPath, method.getReturnType(), holder);
        }
    }

    private static void requireStringType(PsiAnnotation fieldPath, PsiType type, AnnotationHolder holder) {
        PsiType element = type instanceof PsiArrayType array ? array.getComponentType() : type;
        if (element instanceof PsiClassType classType) {
            PsiClass resolved = classType.resolve();
            if (resolved == null || CommonClassNames.JAVA_LANG_STRING.equals(resolved.getQualifiedName())) {
                return;
            }
        }
        report(fieldPath, FieldConstants.unsupportedTypeMessage(type.getCanonicalText()), holder);
    }

    /** Empty when unconstrained; null when the use site cannot supply one, already reported. */
    private static @Nullable String expectedOwner(
            PsiAnnotation fieldPath, PsiNameValuePair pair, AnnotationHolder holder) {
        String fixed = fixedOwner(fieldPath);
        if (!takesReturnType(fieldPath)) {
            return fixed;
        }
        if (!fixed.isEmpty()) {
            return null; // Conflicting owners; the declaration is already flagged.
        }
        String member = pair.getName() == null ? "value" : pair.getName();
        if (!(annotatedDeclaration(pair) instanceof PsiMethod method)) {
            report(pair, FieldConstants.notOnAMethodMessage(member), holder);
            return null;
        }
        PsiType returnType = method.getReturnType();
        PsiClass resolved = returnType instanceof PsiClassType classType ? classType.resolve() : null;
        if (resolved == null || resolved.getQualifiedName() == null) {
            String name = returnType == null ? "?" : returnType.getCanonicalText();
            report(pair, FieldConstants.unusableReturnTypeMessage(member, name), holder);
            return null;
        }
        return resolved.getQualifiedName();
    }

    /** Reached through the annotation's modifier list, so only a direct application counts. */
    private static @Nullable PsiElement annotatedDeclaration(PsiNameValuePair pair) {
        PsiAnnotation annotation = PsiTreeUtil.getParentOfType(pair, PsiAnnotation.class);
        PsiAnnotationOwner owner = annotation == null ? null : annotation.getOwner();
        return owner instanceof PsiModifierList list ? list.getParent() : null;
    }

    private static boolean takesReturnType(PsiAnnotation fieldPath) {
        return Boolean.TRUE.equals(constantOfValue(fieldPath.findAttributeValue("returnType")));
    }

    private static String fixedOwner(PsiAnnotation fieldPath) {
        PsiClass owner = resolveClassValue(fieldPath.findAttributeValue("value"));
        String qualifiedName = owner == null ? null : owner.getQualifiedName();
        return qualifiedName == null || CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName) ? "" : qualifiedName;
    }

    private static @Nullable PsiClass resolveClassValue(@Nullable PsiAnnotationMemberValue value) {
        if (value instanceof PsiClassObjectAccessExpression access) {
            return access.getOperand().getType() instanceof PsiClassType classType ? classType.resolve() : null;
        }
        if (value instanceof PsiExpression expression && expression.getType() instanceof PsiClassType classType) {
            PsiType[] parameters = classType.getParameters();
            return parameters.length == 1 && parameters[0] instanceof PsiClassType parameter
                    ? parameter.resolve()
                    : null;
        }
        return null;
    }

    private static @Nullable PsiAnnotation fieldPathOn(PsiNameValuePair pair) {
        PsiElement resolved = pair.getReference() == null ? null : pair.getReference().resolve();
        return resolved instanceof PsiMethod method
                ? method.getAnnotation(FieldConstants.FIELD_PATH_ANNOTATION)
                : null;
    }

    private static PsiAnnotationMemberValue[] valuesOf(PsiNameValuePair pair) {
        PsiAnnotationMemberValue value = pair.getValue();
        if (value instanceof PsiArrayInitializerMemberValue array) {
            return array.getInitializers();
        }
        return value == null ? new PsiAnnotationMemberValue[0] : new PsiAnnotationMemberValue[] {value};
    }

    private static @Nullable String problemWith(PsiElement value, String expectedOwner) {
        if (!(value instanceof PsiExpression expression) || !(constantOfValue(expression) instanceof String path)) {
            return null;
        }
        String owner = FieldConstants.ownerOf(path);
        if (owner.isEmpty() || !isInstanceField(value, owner, FieldConstants.fieldNameOf(path))) {
            return FieldConstants.notAFieldMessage(path);
        }
        if (!expectedOwner.isEmpty() && !owner.equals(expectedOwner)) {
            return FieldConstants.wrongOwnerMessage(path, owner, expectedOwner);
        }
        return null;
    }

    private static @Nullable Object constantOfValue(@Nullable PsiAnnotationMemberValue value) {
        if (!(value instanceof PsiExpression expression)) {
            return null;
        }
        Object constant = JavaPsiFacade.getInstance(expression.getProject())
                .getConstantEvaluationHelper()
                .computeConstantExpression(expression);
        if (constant == null
                && expression instanceof PsiReferenceExpression reference
                && reference.resolve() instanceof PsiField field) {
            return field.computeConstantValue();
        }
        return constant;
    }

    private static boolean isInstanceField(PsiElement context, String ownerName, String fieldName) {
        PsiClass owner = JavaPsiFacade.getInstance(context.getProject())
                .findClass(ownerName, context.getResolveScope());
        if (owner == null) {
            return false;
        }
        PsiField field = owner.findFieldByName(fieldName, true);
        return field != null && !field.hasModifierProperty(PsiModifier.STATIC);
    }

    private static void report(PsiElement range, String message, AnnotationHolder holder) {
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(range).create();
    }
}
