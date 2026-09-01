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
 * Runs both halves of the field-path system: {@link FieldPath} validation, which works everywhere,
 * and {@link Fields} constant injection, which needs javac internals.
 *
 * <p>The two are deliberately kept apart. Validation is written against supported compiler API and is
 * wired up here directly. Injection is reached only through {@link FieldsInjector}, loaded by name
 * inside a {@code try}, so a compiler that refuses access to its own syntax tree costs a warning
 * rather than the build — this class never mentions a {@code com.sun.tools.javac} type, so nothing
 * fails to resolve when it is loaded.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("*")
public class FieldsProcessor extends AbstractProcessor {

    private static final String INJECTOR = "com.tabariyya.dtogenerator.fields.JavacFieldsInjector";

    private FieldsInjector injector;
    private String injectorFailure;
    private boolean reportedInjectorFailure;

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
     * Attaches the validator to the compiler's task queue. Does nothing at all when this is not
     * javac, or is a javac that will not hand its queue over: paths then go unchecked, which is the
     * same position a build was in before this library existed.
     *
     * <p>The empty {@code started} below is not redundant, whatever an IDE resolving against a
     * modern JDK says: {@code TaskListener} only gained default methods in Java 9, and this library
     * still compiles on 8, where leaving it out does not compile.
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
                    TreePath type = trees.getPath(event.getTypeElement());
                    if (type != null) {
                        new FieldPathValidator(javac, trees).scan(type, null);
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void loadInjector(ProcessingEnvironment javac) {
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

    private void warn(String message, Element element) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message, element);
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
