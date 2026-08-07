package com.tabariyya.dtogenerator.annotations;

public @interface Field {
    Class<?> type();
    String name();
    AddAnnotation[] annotations() default {};
}
