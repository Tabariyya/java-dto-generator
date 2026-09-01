package com.tabariyya.dtogenerator.fields;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an annotation member as holding field paths produced by {@link Fields}. Every value is
 * checked by the compiler and, with the plugin installed, in the editor.
 *
 * <pre>
 * &#64;interface DtoConfig {
 *     &#64;FieldPath                    String[] fields();      // a field of any class
 *     &#64;FieldPath(User.class)        String[] userFields();  // a field of User only
 *     &#64;FieldPath(returnType = true) String[] ownFields();   // a field of the annotated
 * }                                                            // method's return type
 * </pre>
 *
 * <p>Unlike {@link Fields}, this needs no compiler flags — the check is written against supported
 * compiler API and works on every JDK from 8 onwards.
 *
 * <p><b>Where it can be placed</b>
 *
 * <ul>
 *   <li>Only on a member of an {@code @interface}. On any other method it is an <b>error</b>.
 *   <li>{@code @Target(METHOD)} means javac itself rejects it on a parameter, field or variable.
 *   <li>The member's type must be {@code String} or {@code String[]}, otherwise an <b>error</b>.
 *       javac already limits annotation member types, so what is left to reject is primitives,
 *       {@code Class}, enums, annotations and their arrays.
 *   <li>Setting both {@link #value()} and {@link #returnType()} is an <b>error</b>: they are two ways
 *       to name one expected owner.
 * </ul>
 *
 * <p><b>What each value must be</b>
 *
 * <ul>
 *   <li>A path naming a real instance field, otherwise an <b>error</b>.
 *   <li>When an owner is fixed by {@link #value()} or {@link #returnType()}, a path naming exactly
 *       that type.
 *   <li>Checked wherever the annotation is used: on a type, field, constructor, method, parameter or
 *       local variable, and inside a nested annotation.
 * </ul>
 *
 * <p><b>Diagnostics</b>
 *
 * <ul>
 *   <li><b>Always errors, never warnings.</b> A wrong path still compiles to a valid {@code String},
 *       so whatever consumes it — a generator, a mapper, anything reflecting on the path — would only
 *       fail at runtime. Compile time is the last cheap place to catch it.
 *   <li>If a file has an unrelated compile error, attribution may not finish and these diagnostics
 *       may not appear for that file until it does.
 * </ul>
 *
 * <p><b>Why annotation members only</b>
 *
 * <ul>
 *   <li>An annotation argument is guaranteed by the language to be a compile-time constant, so every
 *       value can be checked.
 *   <li>On an ordinary parameter the check could only ever be partial — {@code remove(User.ID)} is
 *       checkable, {@code remove(compute())} is not — and partial validation hides which call sites
 *       are actually guaranteed.
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldPath {

    /**
     * The type each path must name. Defaults to {@code Object.class}, which accepts a field of any
     * class.
     *
     * <ul>
     *   <li>Matching is exact: a path naming a <b>supertype</b> or a <b>subtype</b> is rejected.
     *   <li>That works because {@link Fields} gives every class its own constant for an inherited
     *       field, so {@code Engineer.ID} is {@code "…Engineer#id"} and passes a
     *       {@code @FieldPath(Engineer.class)} member, while {@code User.ID} is {@code "…User#id"}
     *       and does not.
     *   <li>A subtype's own fields do not exist on this type, so accepting them would turn a compile
     *       error into a runtime one.
     * </ul>
     *
     * @return the type every path on this member must name
     */
    Class<?> value() default Object.class;

    /**
     * Takes the expected type from the <b>return type of the method the annotation is applied to</b>,
     * rather than fixing one class here.
     *
     * <pre>
     * &#64;GenerateDto(removeFields = {User.PASSWORD})   // accepted
     * User getUser();
     *
     * &#64;GenerateDto(removeFields = {Account.DATE})    // error: Account#date is a field of
     * User getUser();                                    // Account, but only fields of User
     *                                                    // are allowed here
     * </pre>
     *
     * <ul>
     *   <li>For an annotation whose subject class varies per use site — as {@code @GenerateDto}'s
     *       does, since it reads the source class off the method it annotates — this is the only way
     *       to express the expected owner. {@link #value()} can only name one class for all uses.
     *   <li>Using the annotation somewhere without a return type — on a class, field or parameter —
     *       is an <b>error</b> at that use site.
     *   <li>A return type that is not a class ({@code void}, a primitive, an array, a type variable)
     *       is an <b>error</b> at that use site.
     *   <li>The return type is compared as written, so a generic return like {@code List<User>} names
     *       {@code java.util.List}, not {@code User}.
     * </ul>
     *
     * @return whether the expected type is the annotated method's return type
     */
    boolean returnType() default false;
}
