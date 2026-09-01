package com.tabariyya.dtogenerator.idea;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.tabariyya.dtogenerator.fields.FieldConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FieldsAugmentProvider extends PsiAugmentProvider {

    @Override
    @SuppressWarnings("unchecked")
    protected <Psi extends PsiElement> @NotNull List<Psi> getAugments(
            @NotNull PsiElement element, @NotNull Class<Psi> type, @Nullable String nameHint) {
        if (type != PsiField.class
                || !(element instanceof PsiExtensibleClass psiClass)
                || element instanceof PsiTypeParameter
                || psiClass.getQualifiedName() == null
                || psiClass.isInterface()
                || psiClass.isEnum()
                || psiClass.getAnnotation(FieldConstants.ANNOTATION) == null) {
            return List.of();
        }
        List<PsiField> constants = CachedValuesManager.getCachedValue(
                psiClass,
                () -> CachedValueProvider.Result.create(
                        constantsOf(psiClass), PsiModificationTracker.MODIFICATION_COUNT));
        if (nameHint == null) {
            return (List<Psi>) constants;
        }
        return (List<Psi>) constants.stream()
                .filter(constant -> nameHint.equals(constant.getName()))
                .toList();
    }

    private static List<PsiField> constantsOf(PsiExtensibleClass psiClass) {
        Set<String> declared = declaredNamesOf(psiClass);
        List<PsiField> constants = new ArrayList<>();
        PsiType stringType = PsiType.getJavaLangString(psiClass.getManager(), psiClass.getResolveScope());
        candidates(psiClass, declared).forEach((name, fields) -> {
            if (fields.size() == 1) {
                constants.add(new FieldConstant(
                        psiClass,
                        name,
                        FieldConstants.valueFor(psiClass.getQualifiedName(), fields.getFirst().getName()),
                        stringType,
                        fields.getFirst()));
            }
        });
        return constants;
    }

    static Map<String, List<PsiField>> candidates(PsiClass psiClass, Set<String> declared) {
        Map<String, List<PsiField>> byConstant = new LinkedHashMap<>();
        for (PsiField field : instanceFieldsOf(psiClass)) {
            if (FieldConstants.namesItsOwnConstant(field.getName())) {
                continue;
            }
            String name = FieldConstants.nameFor(field.getName());
            if (declared.contains(name)) {
                continue;
            }
            byConstant.putIfAbsent(name, new ArrayList<>());
            byConstant.get(name).add(field);
        }
        return byConstant;
    }

    static List<PsiField> constantsOf(PsiElement element) {
        if (element instanceof FieldConstant
                || !(element instanceof PsiField field)
                || field.hasModifierProperty(PsiModifier.STATIC)
                || FieldConstants.namesItsOwnConstant(field.getName())) {
            return List.of();
        }
        PsiClass owner = field.getContainingClass();
        if (owner == null) {
            return List.of();
        }
        String name = FieldConstants.nameFor(field.getName());
        List<PsiField> constants = new ArrayList<>();
        addConstant(owner, name, constants);
        ClassInheritorsSearch.search(owner, GlobalSearchScope.projectScope(owner.getProject()), true)
                .forEach(inheritor -> {
                    addConstant(inheritor, name, constants);
                    return true;
                });
        return constants;
    }

    private static void addConstant(PsiClass psiClass, String name, List<PsiField> constants) {
        if (psiClass.findFieldByName(name, false) instanceof FieldConstant constant) {
            constants.add(constant);
        }
    }

    static Set<String> declaredNamesOf(PsiClass psiClass) {
        return ownFieldsOf(psiClass).stream().map(PsiField::getName).collect(Collectors.toSet());
    }

    static List<PsiField> instanceFieldsOf(PsiClass psiClass) {
        List<PsiField> fields = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (PsiClass current = psiClass;
                current != null
                        && !FieldConstants.isJdkType(current.getQualifiedName())
                        && visited.add(current.getQualifiedName());
                current = current.getSuperClass()) {
            for (PsiField field : ownFieldsOf(current)) {
                if (!field.hasModifierProperty(PsiModifier.STATIC) && seen.add(field.getName())) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    static List<PsiField> ownFieldsOf(PsiClass psiClass) {
        return psiClass instanceof PsiExtensibleClass extensible
                ? extensible.getOwnFields()
                : List.of(psiClass.getFields());
    }
}
