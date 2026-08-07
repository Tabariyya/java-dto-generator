package com.tabariyya.dtogenerator.annotations;

import java.lang.annotation.Annotation;

public @interface AddAnnotation {
    Class<? extends Annotation> value();
    String params() default "";
}
