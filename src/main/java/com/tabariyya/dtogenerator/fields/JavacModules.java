package com.tabariyya.dtogenerator.fields;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Opens javac's internal packages to this processor from inside the compiler's own JVM, so consumers
 * need no {@code --add-exports} flags.
 *
 * <p>This is what Lombok does, and for the same reason: no supported API lets a processor reach the
 * syntax tree it has to rewrite, and requiring every consumer to fork their compiler is not a real
 * option. The module system offers no sanctioned way in, so the only route is {@code Unsafe} to
 * defeat the access check on {@code Module.implAddExports}. That is a deliberate hack on JDK
 * internals, and it has needed revisiting at past JDK boundaries.
 *
 * <p>Every failure here is silent. When the packages cannot be opened, {@link FieldsProcessor} falls
 * back to reporting the flags, which still work.
 */
final class JavacModules {

    private static final String[] PACKAGES = {
        "com.sun.tools.javac.code",
        "com.sun.tools.javac.processing",
        "com.sun.tools.javac.tree",
        "com.sun.tools.javac.util",
    };

    private JavacModules() {}

    /** Java 8 has no module system and needs nothing; anything later is opened here or not at all. */
    static void openJavacTo(Class<?> processor) {
        try {
            Class<?> module = Class.forName("java.lang.Module");
            Object javac = bootModule("jdk.compiler");
            Object own = Class.class.getMethod("getModule").invoke(processor);
            if (javac == null || own == null || alreadyExported(module, javac, own)) {
                return;
            }
            grant(module.getDeclaredMethod("implAddExports", String.class, module), javac, own);
            grant(module.getDeclaredMethod("implAddOpens", String.class, module), javac, own);
        } catch (Throwable noModuleSystemOrNoWayIn) {
        }
    }

    /**
     * Checked through public API before anything else, because the alternative reaches for
     * {@code Unsafe}, and calling it prints a deprecation warning naming this class on Java 24 and
     * later. A build that passes the {@code --add-exports} flags never gets that far.
     */
    private static boolean alreadyExported(Class<?> module, Object javac, Object own) throws Exception {
        Method isExported = module.getMethod("isExported", String.class, module);
        for (String each : PACKAGES) {
            if (!Boolean.TRUE.equals(isExported.invoke(javac, each, own))) {
                return false;
            }
        }
        return true;
    }

    private static void grant(Method addTo, Object javac, Object own) {
        try {
            makeInvokable(addTo);
            for (String each : PACKAGES) {
                addTo.invoke(javac, each, own);
            }
        } catch (Throwable refused) {
        }
    }

    private static Object bootModule(String name) throws Exception {
        Class<?> layer = Class.forName("java.lang.ModuleLayer");
        Object boot = layer.getMethod("boot").invoke(null);
        Object found = layer.getMethod("findModule", String.class).invoke(boot, name);
        return ((Optional<?>) found).orElse(null);
    }

    /**
     * {@code setAccessible} cannot open {@code java.lang} to us — that is the very thing being asked
     * for — so the flag it would have set is written directly, at the offset the field occupies in a
     * class laid out like {@code AccessibleObject}.
     */
    private static void makeInvokable(Method method) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);

        Method offsetOf = unsafeType.getMethod("objectFieldOffset", Field.class);
        Method writeBoolean = unsafeType.getMethod("putBoolean", Object.class, long.class, boolean.class);
        Field mirrored = AccessibleObjectLayout.class.getDeclaredField("override");
        writeBoolean.invoke(unsafe, method, offsetOf.invoke(unsafe, mirrored), true);
    }

    /** Mirrors {@code AccessibleObject}'s field layout, so {@code override} sits at the same offset. */
    @SuppressWarnings("unused")
    private static final class AccessibleObjectLayout {
        boolean override;
        Object accessCheckCache;
    }
}
