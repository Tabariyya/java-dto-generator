package com.tabariyya.dtogenerator.idea;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightFieldBuilder;
import org.jetbrains.annotations.NotNull;

class FieldConstant extends LightFieldBuilder {

    private final String value;

    FieldConstant(@NotNull PsiClass owner, @NotNull String name, @NotNull String value, @NotNull PsiType type,
            @NotNull PsiField source) {
        super(owner.getManager(), name, type);
        this.value = value;
        setContainingClass(owner);
        setModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL);
        setNavigationElement(source);
        setInitializer(JavaPsiFacade.getElementFactory(owner.getProject())
                .createExpressionFromText('"' + value + '"', owner));
    }

    @Override
    public boolean hasInitializer() {
        return true;
    }

    @Override
    public PsiFile getContainingFile() {
        PsiClass owner = getContainingClass();
        return owner == null ? super.getContainingFile() : owner.getContainingFile();
    }

    @Override
    public Object computeConstantValue() {
        return value;
    }
}
