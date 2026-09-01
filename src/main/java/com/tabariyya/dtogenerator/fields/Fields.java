package com.tabariyya.dtogenerator.fields;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a {@code public static final String} constant for every instance field of the annotated
 * class, so a field can be named without writing the name as a string.
 *
 * <pre>
 * &#64;Fields
 * class User {
 *     private UUID id;
 *     // injected: public static final String ID = "com.acme.User#id";
 * }
 * </pre>
 *
 * <p><b>What is generated</b>
 *
 * <ul>
 *   <li>One constant per instance field — the class's own and the ones it inherits.
 *   <li>Named in {@code UPPER_SNAKE_CASE}, breaking a word only where an uppercase run ends, so
 *       {@code userID} gives {@code USER_ID} and {@code htmlURLParser} gives {@code HTML_URL_PARSER}.
 *   <li>Holding {@code <annotated class>#<field name>}, with the class's canonical name.
 *   <li>Real compile-time constants, usable as annotation arguments (see {@link FieldPath}), in
 *       {@code switch} labels, and from any downstream compilation.
 * </ul>
 *
 * <p><b>Requires compiler flags.</b> Injection rewrites the class's syntax tree, which needs javac
 * internals the JDK does not export. Unlike {@link FieldPath}, this does not work out of the box:
 *
 * <ul>
 *   <li>On <b>Java 8</b> nothing is needed.
 *   <li>On <b>Java 9 and later</b> the compiler must be forked and given
 *       {@code -J--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED} and the same for
 *       {@code .processing}, {@code .tree} and {@code .util}. See the README for the
 *       {@code maven-compiler-plugin} block.
 *   <li>These are <b>JVM arguments, not compiler options</b>. The processor runs inside the
 *       compiler's own JVM, so only that JVM's module graph governs what it can reach; javac also
 *       rejects a plain {@code --add-exports} option outright when the target is 8.
 *   <li>Without them, the processor reports a <b>warning</b> naming the missing flags and generates
 *       nothing. It never fails the build, so a project that only uses {@link FieldPath} or
 *       {@code @GenerateDto} is unaffected by this annotation existing on the classpath.
 * </ul>
 *
 * <p><b>Where it applies</b>
 *
 * <ul>
 *   <li><b>Classes</b>, including abstract, final, generic, nested and inner ones.
 *   <li><b>Interfaces</b> and {@code @interface} types — nothing generated, <b>warning</b>; an
 *       interface cannot declare instance fields.
 *   <li><b>Enums</b> — nothing generated, <b>warning</b>; enum constants are already valid annotation
 *       values, so a generated path would add nothing.
 *   <li><b>Local classes</b> declared inside a method — nothing generated and <b>no warning</b>,
 *       because annotation processing never reports them. Known gap.
 *   <li><b>Anonymous classes</b> cannot be annotated at all.
 * </ul>
 *
 * <p><b>Inheritance</b>
 *
 * <ul>
 *   <li>A subclass gets its own constant for an inherited field, named after the subclass:
 *       {@code Engineer.ID} is {@code "…Engineer#id"} while {@code User.ID} is {@code "…User#id"}.
 *   <li>One field therefore has one path per class that inherits it. A consumer mapping paths back to
 *       columns must resolve them per class.
 *   <li>The supertype need not be annotated — it can come from a jar you cannot modify.
 *   <li>The walk stops at {@code java.*}, {@code javax.*} and {@code jdk.*}, so a JDK base class
 *       contributes nothing.
 * </ul>
 *
 * <p><b>When a constant is omitted</b> — each is a <b>warning</b>, so nothing is dropped silently.
 *
 * <ul>
 *   <li>The field's name is already in constant form ({@code FOO}), so the constant would collide
 *       with the field itself.
 *   <li>The class already declares a field with the constant's name. This is how you hand-write a
 *       constant to override the generated one; yours is kept untouched.
 *   <li>Two fields map to one constant name ({@code firstName} and {@code first_name}) — neither gets
 *       it, because picking one would be arbitrary.
 * </ul>
 *
 * <p>Static fields are never candidates and are skipped without a warning.
 *
 * <p><b>Diagnostics</b>
 *
 * <ul>
 *   <li><b>Never errors.</b> A class that compiles still compiles after this annotation is added.
 *   <li>Every problem is a warning, because a missing constant announces itself as an ordinary
 *       "cannot find symbol" the moment you try to use it.
 * </ul>
 *
 * <p><b>Also worth knowing</b>
 *
 * <ul>
 *   <li>Retention is {@code SOURCE}; the annotation itself is not in the class file.
 *   <li>The path's class part is canonical, so a nested class reads {@code a.b.Outer.Inner#field} —
 *       which {@code Class.forName} does not accept ({@code a.b.Outer$Inner} does).
 *   <li>The IDE resolves these constants only with the accompanying IntelliJ plugin installed.
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Fields {}
