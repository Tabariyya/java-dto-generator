package com.tabariyya.dtogenerator.idea;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.List;
import java.util.Set;
import com.tabariyya.dtogenerator.fields.FieldConstants;
import org.jetbrains.annotations.NotNull;

public class FieldsAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiClass psiClass)) {
            return;
        }
        PsiAnnotation fields = psiClass.getAnnotation(FieldConstants.ANNOTATION);
        if (fields == null) {
            return;
        }
        if (psiClass.isInterface()) {
            report(holder, fields, FieldConstants.notSupportedOnInterfacesMessage());
        } else if (psiClass.isEnum()) {
            report(holder, fields, FieldConstants.notSupportedOnEnumsMessage());
        } else {
            annotateNaming(psiClass, fields, holder);
        }
    }

    private static void annotateNaming(PsiClass psiClass, PsiAnnotation fields, AnnotationHolder holder) {
        Set<String> declared = FieldsAugmentProvider.declaredNamesOf(psiClass);
        for (PsiField field : FieldsAugmentProvider.instanceFieldsOf(psiClass)) {
            if (FieldConstants.namesItsOwnConstant(field.getName())) {
                report(holder, fields, FieldConstants.nameIsAlreadyConstantMessage(field.getName()));
                continue;
            }
            String constant = FieldConstants.nameFor(field.getName());
            if (declared.contains(constant)) {
                report(holder, fields, FieldConstants.nameTakenMessage(constant, field.getName()));
            }
        }
        FieldsAugmentProvider.candidates(psiClass, declared).forEach((constant, colliding) -> {
            if (colliding.size() > 1) {
                List<String> names = colliding.stream().map(PsiField::getName).toList();
                report(holder, fields, FieldConstants.collisionMessage(constant, names));
            }
        });
    }

    private static void report(AnnotationHolder holder, PsiAnnotation fields, String message) {
        holder.newAnnotation(HighlightSeverity.WARNING, message).range(fields).create();
    }
}
