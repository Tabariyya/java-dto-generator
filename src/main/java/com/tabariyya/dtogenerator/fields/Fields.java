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
 * <p>Constants are named in {@code UPPER_SNAKE_CASE}, breaking a word only where an uppercase run
 * ends, so {@code htmlURLParser} gives {@code HTML_URL_PARSER}. Inherited fields get one too, named
 * after the class that inherits them, so {@code Engineer.ID} and {@code User.ID} differ; the
 * supertype need not be annotated, and the walk stops at {@code java.*}, {@code javax.*} and
 * {@code jdk.*}.
 *
 * <p><b>Never fails a build.</b> Every problem is a warning, because a missing constant announces
 * itself as an ordinary "cannot find symbol" where you use it. Nothing is generated, with a warning,
 * for an interface or an enum, for a field already named in constant form, for a name the class
 * already declares, or for a name two fields both want. Static fields are skipped silently. A local
 * class gets nothing and no warning, since annotation processing never reports one.
 *
 * <p><b>Needs no build configuration.</b> Injection rewrites the syntax tree through javac internals,
 * which the module system closes on Java 9 and later, so the processor opens them to itself at
 * startup the way Lombok does. On Java 24 and later that costs a {@code sun.misc.Unsafe} deprecation
 * warning naming {@code JavacModules}; passing the {@code --add-exports} flags from the README
 * silences it, since the processor then finds the packages already open and does nothing.
 *
 * <p>Retention is {@code SOURCE}. The path's class part is canonical, so a nested class reads
 * {@code a.b.Outer.Inner#field}, which {@code Class.forName} does not accept. The IDE resolves these
 * constants only with the accompanying plugin installed.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Fields {}
