package com.tabariyya.dtogenerator.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface GenerateDto {
    String[] removeFields() default {};
    Field[] addFields() default {};

    /** Class the generated DTO extends. Object (the default) means no extends clause. */
    Class<?> extend() default Object.class;
}
