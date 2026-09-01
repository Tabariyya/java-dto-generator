package com.tabariyya.dtogenerator.fields;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an annotation member as holding field paths produced by {@link Fields}, and checks every
 * value in the compiler and, with the plugin installed, in the editor.
 *
 * <pre>
 * &#64;interface DtoConfig {
 *     &#64;FieldPath                    String[] fields();      // a field of any class
 *     &#64;FieldPath(User.class)        String[] userFields();  // a field of User only
 *     &#64;FieldPath(returnType = true) String[] ownFields();   // a field of the annotated
 * }                                                            // method's return type
 * </pre>
 *
 * <p>Unlike {@link Fields} this needs no compiler flags, and works from Java 8 onwards.
 *
 * <p><b>Always errors, never warns.</b> A wrong path is still a valid {@code String}, so whatever
 * consumes it would only fail at runtime. It is an error to use this outside an {@code @interface},
 * on a member that is not {@code String} or {@code String[]}, to set both {@link #value()} and
 * {@link #returnType()}, or to give a value that names no real instance field. Values are checked
 * wherever the annotation is used, nested annotations included.
 *
 * <p>Only annotation members are supported, because an annotation argument is guaranteed to be a
 * compile-time constant. On an ordinary parameter the check could only ever be partial, which would
 * hide which call sites are actually guaranteed.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldPath {

    /**
     * The type each path must name; {@code Object.class} accepts a field of any class.
     *
     * <p>Matching is exact — a path naming a supertype or a subtype is rejected. {@link Fields} gives
     * every class its own constant for an inherited field, so {@code Engineer.ID} passes a
     * {@code @FieldPath(Engineer.class)} member and {@code User.ID} does not.
     *
     * @return the type every path on this member must name
     */
    Class<?> value() default Object.class;

    /**
     * Takes the expected type from the return type of the method the annotation is applied to,
     * rather than fixing one class here.
     *
     * <pre>
     * &#64;GenerateDto(removeFields = {User.PASSWORD})   // accepted
     * User getUser();
     *
     * &#64;GenerateDto(removeFields = {Account.DATE})    // error
     * User getUser();
     * </pre>
     *
     * <p>For an annotation whose subject class varies per use site, as {@code @GenerateDto}'s does,
     * this is the only way to express the expected owner. Using it where there is no return type, or
     * where the return type is not a class, is an error at that use site. The return type is compared
     * as written, so {@code List<User>} names {@code java.util.List}.
     *
     * @return whether the expected type is the annotated method's return type
     */
    boolean returnType() default false;
}
