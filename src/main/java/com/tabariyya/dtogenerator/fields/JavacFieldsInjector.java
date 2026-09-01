package com.tabariyya.dtogenerator.fields;

import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * Appends the constants to the annotated class's syntax tree, the way Lombok adds accessors.
 *
 * <p>Every {@code com.sun.tools.javac} reference in the library is confined to this class. It is
 * named, never imported, by {@link FieldsProcessor}, so on a compiler that does not export those
 * packages the failure is a caught {@code IllegalAccessError} at load time rather than a broken
 * build for everyone with this library on their classpath.
 */
final class JavacFieldsInjector implements FieldsInjector {

    private ProcessingEnvironment processingEnv;
    private Trees trees;
    private TreeMaker maker;
    private Names names;

    private final Set<String> injected = new LinkedHashSet<String>();

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.trees = Trees.instance(processingEnv);
        this.maker = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }

    @Override
    public void inject(TypeElement type) {
        Object tree = trees.getTree(type);
        if (!(tree instanceof JCClassDecl) || !injected.add(type.getQualifiedName().toString())) {
            return;
        }
        JCClassDecl classDecl = (JCClassDecl) tree;
        Set<String> declaredNames = declaredNamesOf(classDecl);
        maker.at(classDecl.pos);
        String qualifiedName = type.getQualifiedName().toString();

        for (Map.Entry<String, List<String>> constant :
                candidates(ElementFields.instanceNamesOf(type), declaredNames, type).entrySet()) {
            List<String> fieldNames = constant.getValue();
            if (fieldNames.size() > 1) {
                warn(FieldConstants.collisionMessage(constant.getKey(), fieldNames), type);
                continue;
            }
            classDecl.defs = classDecl.defs.append(maker.VarDef(
                    maker.Modifiers(Flags.PUBLIC | Flags.STATIC | Flags.FINAL),
                    names.fromString(constant.getKey()),
                    stringType(),
                    maker.Literal(FieldConstants.valueFor(qualifiedName, fieldNames.get(0)))));
        }
    }

    /** Constant name to the fields wanting it, so a name wanted by two fields is visible as one. */
    private Map<String, List<String>> candidates(
            List<String> fieldNames, Set<String> declaredNames, TypeElement type) {
        Map<String, List<String>> byConstant = new LinkedHashMap<String, List<String>>();
        for (String fieldName : fieldNames) {
            if (FieldConstants.namesItsOwnConstant(fieldName)) {
                warn(FieldConstants.nameIsAlreadyConstantMessage(fieldName), type);
                continue;
            }
            String constant = FieldConstants.nameFor(fieldName);
            if (declaredNames.contains(constant)) {
                warn(FieldConstants.nameTakenMessage(constant, fieldName), type);
                continue;
            }
            List<String> wanting = byConstant.get(constant);
            if (wanting == null) {
                wanting = new ArrayList<String>();
                byConstant.put(constant, wanting);
            }
            wanting.add(fieldName);
        }
        return byConstant;
    }

    /**
     * Read before anything is appended. Checking the class's own declarations afterwards would see
     * the constants this run just added and report every one of them as a name already taken.
     */
    private static Set<String> declaredNamesOf(JCClassDecl classDecl) {
        Set<String> declared = new LinkedHashSet<String>();
        for (JCTree member : classDecl.defs) {
            if (member instanceof JCVariableDecl) {
                declared.add(((JCVariableDecl) member).name.toString());
            }
        }
        return declared;
    }

    private JCExpression stringType() {
        return maker.Select(
                maker.Select(maker.Ident(names.fromString("java")), names.fromString("lang")),
                names.fromString("String"));
    }

    private void warn(String message, TypeElement type) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message, type);
    }
}
