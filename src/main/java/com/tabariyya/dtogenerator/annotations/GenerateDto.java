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
     * Fields of the source class to leave out of the generated DTO.
     *
     * <p>The source class is this method's return type, so {@link FieldPath#returnType()} checks
     * every value against it: a field of another class, or a name that is not a field at all, is a
     * compile error rather than a silently ignored string.
     *
     * <pre>
     * &#64;GenerateDto(removeFields = {User.PASSWORD})   // User must be annotated {@link Fields}
     * User getUser();
     * </pre>
     *
     * <p>Bare field names — {@code removeFields = {"password"}} — are still understood by the
     * generator, but no longer accepted by the compiler, because nothing checks that such a name
     * survives a rename of the field.
     */
    @FieldPath(returnType = true)
    String[] removeFields() default {};

    Field[] addFields() default {};

    /** Class the generated DTO extends. Object (the default) means no extends clause. */
    Class<?> extend() default Object.class;
}
