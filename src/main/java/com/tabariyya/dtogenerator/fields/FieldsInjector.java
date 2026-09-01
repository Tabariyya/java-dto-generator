package com.tabariyya.dtogenerator.fields;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * Adds the constants to a class.
 *
 * <p>An interface only so {@link FieldsProcessor} can name the operation without naming the javac
 * internals that implement it. The processor is loaded by javac's {@code ServiceLoader} in every
 * build that has this library on the classpath, including builds that never use {@link Fields}; if
 * it referenced {@code com.sun.tools.javac} directly, resolving those references would fail the
 * build on any JDK 9+ compiler run without {@code --add-exports}.
 */
interface FieldsInjector {

    void init(ProcessingEnvironment processingEnv);

    /** Injects the constants, reporting each omission as a warning. */
    void inject(TypeElement type);
}
