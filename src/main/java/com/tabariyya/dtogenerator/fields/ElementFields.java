package com.tabariyya.dtogenerator.fields;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/**
 * Shared by the injector and the validator on purpose: one generates the constants and the other
 * decides whether a path names a real field, so both must walk the hierarchy the same way.
 */
final class ElementFields {

    private ElementFields() {}

    /** The class's own first, then each superclass's, without duplicates. */
    static List<String> instanceNamesOf(TypeElement type) {
        Set<String> fieldNames = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        for (TypeElement current = type;
                current != null && visited.add(current.getQualifiedName().toString());
                current = superclassOf(current)) {
            for (VariableElement field : ElementFilter.fieldsIn(current.getEnclosedElements())) {
                if (!field.getModifiers().contains(Modifier.STATIC)) {
                    fieldNames.add(field.getSimpleName().toString());
                }
            }
        }
        return new ArrayList<>(fieldNames);
    }

    /** The superclass to continue the walk with, or null at the top or at a JDK class. */
    static TypeElement superclassOf(TypeElement type) {
        TypeMirror superclass = type.getSuperclass();
        if (!(superclass instanceof DeclaredType)) {
            return null;
        }
        javax.lang.model.element.Element element = ((DeclaredType) superclass).asElement();
        if (!(element instanceof TypeElement)) {
            return null;
        }
        TypeElement superType = (TypeElement) element;
        return FieldConstants.isJdkType(superType.getQualifiedName().toString()) ? null : superType;
    }
}
