package com.tabariyya.dtogenerator.annotations;

import com.tabariyya.dtogenerator.fields.FieldPath;
import com.tabariyya.dtogenerator.fields.Fields;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface GenerateDto {

    /**
     * Fields of the source class to leave out. The source class is this method's return type, so
     * {@link FieldPath#returnType()} checks every value against it.
     *
     * <pre>
     * &#64;GenerateDto(removeFields = {User.PASSWORD})   // User must be annotated {@link Fields}
     * User getUser();
     * </pre>
     *
     * <p>Bare names such as {@code {"password"}} no longer compile: nothing checks that such a string
     * survives a rename.
     */
    @FieldPath(returnType = true)
    String[] removeFields() default {};

    Field[] addFields() default {};

    /** Class the generated DTO extends. Object (the default) means no extends clause. */
    Class<?> extend() default Object.class;
}
