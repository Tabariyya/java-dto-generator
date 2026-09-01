package com.tabariyya.dtogenerator.fields;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

/**
 * Reports every {@link FieldPath} violation as a compile error.
 *
 * <p>Written entirely against supported compiler API — {@code com.sun.source} and
 * {@code javax.lang.model} — so it needs none of the {@code --add-exports} flags {@link Fields}
 * injection does, and works unchanged from Java 8 onwards.
 *
 * <p>It runs from a {@code TaskListener} once a file finishes {@code ANALYZE}, not during the
 * annotation processing round: annotation arguments are not attributed yet while processing runs, so
 * every value would read as {@code <error>}.
 */
final class FieldPathValidator extends TreePathScanner<Void, Void> {

    private final ProcessingEnvironment processingEnv;
    private final Trees trees;

    FieldPathValidator(ProcessingEnvironment processingEnv, Trees trees) {
        this.processingEnv = processingEnv;
        this.trees = trees;
    }

    @Override
    public Void visitAnnotation(AnnotationTree node, Void unused) {
        TreePath annotationPath = getCurrentPath();
        for (ExpressionTree argument : node.getArguments()) {
            TreePath argumentPath = new TreePath(annotationPath, argument);
            ExecutableElement member = memberOf(node, argumentPath);
            FieldPath fieldPath = member == null ? null : member.getAnnotation(FieldPath.class);
            if (fieldPath == null) {
                continue;
            }
            String expectedOwner = expectedOwner(fieldPath, member, annotationPath);
            if (expectedOwner != null) {
                validate(valuePath(argumentPath), expectedOwner);
            }
        }
        return super.visitAnnotation(node, unused);
    }

    @Override
    public Void visitMethod(MethodTree node, Void unused) {
        Element element = elementOf(getCurrentPath());
        if (element instanceof ExecutableElement) {
            ExecutableElement method = (ExecutableElement) element;
            FieldPath fieldPath = method.getAnnotation(FieldPath.class);
            if (fieldPath != null) {
                checkDeclaration(node, method, fieldPath);
            }
        }
        return super.visitMethod(node, unused);
    }

    private void checkDeclaration(MethodTree node, ExecutableElement method, FieldPath fieldPath) {
        Tree position = fieldPathAnnotationIn(node.getModifiers(), getCurrentPath());
        if (position == null) {
            position = node;
        }
        if (method.getEnclosingElement().getKind() != ElementKind.ANNOTATION_TYPE) {
            report(position, FieldConstants.notAnAnnotationMemberMessage());
        }
        if (fieldPath.returnType() && !fixedOwner(fieldPath).isEmpty()) {
            report(position, FieldConstants.conflictingOwnerMessage());
        }
        checkStringType(method.getReturnType(), position);
    }

    private void checkStringType(TypeMirror type, Tree position) {
        TypeMirror element = type instanceof ArrayType ? ((ArrayType) type).getComponentType() : type;
        if (element.getKind() == TypeKind.ERROR || String.class.getName().equals(element.toString())) {
            return;
        }
        report(position, FieldConstants.unsupportedTypeMessage(type.toString()));
    }

    /**
     * The class every path on this member must name: empty when unconstrained, or null when the use
     * site cannot supply one — in which case the reason has already been reported.
     */
    private String expectedOwner(FieldPath fieldPath, ExecutableElement member, TreePath annotationPath) {
        String fixed = fixedOwner(fieldPath);
        if (!fieldPath.returnType()) {
            return fixed;
        }
        if (!fixed.isEmpty()) {
            return null; // Conflicting owners; the declaration is already flagged.
        }
        TreePath declaration = annotatedDeclaration(annotationPath);
        String member1 = member.getSimpleName().toString();
        if (declaration == null || !(declaration.getLeaf() instanceof MethodTree)) {
            report(annotationPath.getLeaf(), FieldConstants.notOnAMethodMessage(member1));
            return null;
        }
        Element annotated = elementOf(declaration);
        TypeMirror returnType = annotated instanceof ExecutableElement
                ? ((ExecutableElement) annotated).getReturnType()
                : null;
        if (!(returnType instanceof DeclaredType)) {
            report(
                    annotationPath.getLeaf(),
                    FieldConstants.unusableReturnTypeMessage(
                            member1, returnType == null ? "?" : returnType.toString()));
            return null;
        }
        Element returnElement = ((DeclaredType) returnType).asElement();
        if (!(returnElement instanceof TypeElement)) {
            report(
                    annotationPath.getLeaf(),
                    FieldConstants.unusableReturnTypeMessage(member1, returnType.toString()));
            return null;
        }
        return ((TypeElement) returnElement).getQualifiedName().toString();
    }

    /** The declaration an annotation is applied to, reached through its modifiers. */
    private static TreePath annotatedDeclaration(TreePath annotationPath) {
        TreePath modifiers = annotationPath.getParentPath();
        if (modifiers == null || !(modifiers.getLeaf() instanceof ModifiersTree)) {
            return null;
        }
        return modifiers.getParentPath();
    }

    private static String fixedOwner(FieldPath fieldPath) {
        String owner;
        try {
            owner = fieldPath.value().getCanonicalName();
        } catch (MirroredTypeException e) {
            owner = e.getTypeMirror().toString();
        }
        return Object.class.getName().equals(owner) ? "" : owner;
    }

    private void validate(TreePath valuePath, String expectedOwner) {
        if (valuePath.getLeaf() instanceof NewArrayTree) {
            for (ExpressionTree element : ((NewArrayTree) valuePath.getLeaf()).getInitializers()) {
                validate(new TreePath(valuePath, element), expectedOwner);
            }
            return;
        }
        String path = constantOf(valuePath);
        if (path == null) {
            return;
        }
        String problem = problemWith(path, expectedOwner);
        if (problem != null) {
            report(valuePath.getLeaf(), problem);
        }
    }

    private String problemWith(String path, String expectedOwner) {
        String owner = FieldConstants.ownerOf(path);
        TypeElement ownerType = owner.isEmpty() ? null : processingEnv.getElementUtils().getTypeElement(owner);
        if (ownerType == null || !ElementFields.hasInstanceField(ownerType, FieldConstants.fieldNameOf(path))) {
            return FieldConstants.notAFieldMessage(path);
        }
        if (!expectedOwner.isEmpty() && !owner.equals(expectedOwner)) {
            return FieldConstants.wrongOwnerMessage(path, owner, expectedOwner);
        }
        return null;
    }

    private String constantOf(TreePath valuePath) {
        Tree leaf = valuePath.getLeaf();
        if (leaf instanceof LiteralTree) {
            Object value = ((LiteralTree) leaf).getValue();
            return value instanceof String ? (String) value : null;
        }
        Element element = elementOf(valuePath);
        if (!(element instanceof VariableElement)) {
            return null;
        }
        Object constant = ((VariableElement) element).getConstantValue();
        return constant instanceof String ? (String) constant : null;
    }

    /** The annotation member an argument assigns to, resolving the {@code value} shorthand. */
    private ExecutableElement memberOf(AnnotationTree annotation, TreePath argumentPath) {
        if (argumentPath.getLeaf() instanceof AssignmentTree) {
            ExpressionTree name = ((AssignmentTree) argumentPath.getLeaf()).getVariable();
            Element member = elementOf(new TreePath(argumentPath, name));
            return member instanceof ExecutableElement ? (ExecutableElement) member : null;
        }
        Element type = elementOf(new TreePath(argumentPath.getParentPath(), annotation.getAnnotationType()));
        if (!(type instanceof TypeElement)) {
            return null;
        }
        for (ExecutableElement member : ElementFilter.methodsIn(type.getEnclosedElements())) {
            if (member.getSimpleName().contentEquals("value")) {
                return member;
            }
        }
        return null;
    }

    private static TreePath valuePath(TreePath argumentPath) {
        return argumentPath.getLeaf() instanceof AssignmentTree
                ? new TreePath(argumentPath, ((AssignmentTree) argumentPath.getLeaf()).getExpression())
                : argumentPath;
    }

    private Tree fieldPathAnnotationIn(ModifiersTree modifiers, TreePath methodPath) {
        TreePath modifiersPath = new TreePath(methodPath, modifiers);
        for (AnnotationTree annotation : modifiers.getAnnotations()) {
            TreePath annotationPath = new TreePath(modifiersPath, annotation);
            Element type = elementOf(new TreePath(annotationPath, annotation.getAnnotationType()));
            if (type instanceof TypeElement
                    && ((TypeElement) type)
                            .getQualifiedName()
                            .contentEquals(FieldConstants.FIELD_PATH_ANNOTATION)) {
                return annotation;
            }
        }
        return null;
    }

    private Element elementOf(TreePath path) {
        try {
            return trees.getElement(path);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void report(Tree position, String message) {
        trees.printMessage(Diagnostic.Kind.ERROR, message, position, getCurrentPath().getCompilationUnit());
    }
}
