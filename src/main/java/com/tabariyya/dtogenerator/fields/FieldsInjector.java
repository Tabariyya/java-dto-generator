package com.tabariyya.dtogenerator.fields;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * An interface only so {@link FieldsProcessor} can name the operation without naming the javac
 * internals behind it. The processor is loaded in every build that has this library on the
 * classpath, so a direct reference would fail builds that never use {@link Fields}.
 */
interface FieldsInjector {

    void init(ProcessingEnvironment processingEnv);

    void inject(TypeElement type);
}
