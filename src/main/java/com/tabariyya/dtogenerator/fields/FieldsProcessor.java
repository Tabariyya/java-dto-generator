package com.tabariyya.dtogenerator.fields;

import com.google.auto.service.AutoService;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * Runs both halves of the system. Validation uses supported API and is wired up here. Injection is
 * reached only through {@link FieldsInjector}, loaded by name inside a {@code try}, so this class
 * never mentions a {@code com.sun.tools.javac} type and a compiler that refuses access to its own
 * syntax tree costs a warning rather than the build.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("*")
public class FieldsProcessor extends AbstractProcessor {

    private static final String INJECTOR = "com.tabariyya.dtogenerator.fields.JavacFieldsInjector";

    private FieldsInjector injector;
    private String injectorFailure;
    private boolean reportedInjectorFailure;
    private boolean reportedValidatorFailure;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        ProcessingEnvironment javac = ProcessingEnvironments.unwrap(processingEnv);
        validateFieldPaths(javac);
        loadInjector(javac);
    }

    /**
     * Silently leaves paths unchecked when this is not javac. The empty {@code started} below is not
     * redundant, whatever an IDE resolving against a modern JDK says: {@code TaskListener} only
     * gained default methods in Java 9, and this still compiles on 8.
     */
    private void validateFieldPaths(final ProcessingEnvironment javac) {
        final Trees trees;
        try {
            trees = Trees.instance(javac);
            JavacTask.instance(javac).addTaskListener(new TaskListener() {
                @Override
                @SuppressWarnings("RedundantMethodOverride")
                public void started(TaskEvent event) {}

                @Override
                public void finished(TaskEvent event) {
                    if (event.getKind() != TaskEvent.Kind.ANALYZE || event.getTypeElement() == null) {
                        return;
                    }
                    try {
                        TreePath type = trees.getPath(event.getTypeElement());
                        if (type != null) {
                            new FieldPathValidator(javac, trees).scan(type, null);
                        }
                    } catch (Throwable failure) {
                        reportValidatorFailure(failure);
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void loadInjector(ProcessingEnvironment javac) {
        JavacModules.openJavacTo(getClass());
        try {
            FieldsInjector loaded =
                    (FieldsInjector) Class.forName(INJECTOR).getDeclaredConstructor().newInstance();
            loaded.init(javac);
            injector = loaded;
        } catch (Throwable failure) {
            injector = null;
            injectorFailure = describe(failure);
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Fields.class)) {
            if (!(element instanceof TypeElement)) {
                continue;
            }
            if (element.getKind() == ElementKind.INTERFACE || element.getKind() == ElementKind.ANNOTATION_TYPE) {
                warn(FieldConstants.notSupportedOnInterfacesMessage(), element);
            } else if (element.getKind() == ElementKind.ENUM) {
                warn(FieldConstants.notSupportedOnEnumsMessage(), element);
            } else if (injector == null) {
                reportInjectorFailure(element);
            } else {
                injector.inject((TypeElement) element);
            }
        }
        return false;
    }

    private void reportInjectorFailure(Element element) {
        if (!reportedInjectorFailure) {
            reportedInjectorFailure = true;
            warn(FieldConstants.injectionUnavailableMessage(injectorFailure), element);
        }
    }

    /**
     * The listener runs for every class in every build with this library on the classpath, so a
     * defect in the validator must cost a warning rather than someone else's build.
     */
    private void reportValidatorFailure(Throwable failure) {
        if (!reportedValidatorFailure) {
            reportedValidatorFailure = true;
            processingEnv
                    .getMessager()
                    .printMessage(
                            Diagnostic.Kind.WARNING,
                            "@FieldPath values went unchecked: " + describe(failure));
        }
    }

    private void warn(String message, Element element) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message, element);
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
