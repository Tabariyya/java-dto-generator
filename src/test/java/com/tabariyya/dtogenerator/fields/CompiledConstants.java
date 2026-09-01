package com.tabariyya.dtogenerator.fields;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Reads an injected constant out of the compiled class file, which is what proves it is real: it has
 * to have survived as a {@code ConstantValue} to be readable here.
 */
final class CompiledConstants {

    private final File classesDir;

    CompiledConstants(File classesDir) {
        this.classesDir = classesDir;
    }

    /** Null when the class does not declare it. */
    String of(String qualifiedClassName, String constantName) {
        URLClassLoader loader = null;
        try {
            loader = new URLClassLoader(
                    new URL[] {classesDir.toURI().toURL()}, CompiledConstants.class.getClassLoader());
            return String.valueOf(loader.loadClass(qualifiedClassName).getField(constantName).get(null));
        } catch (ReflectiveOperationException absent) {
            return null;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            close(loader);
        }
    }

    private static void close(URLClassLoader loader) {
        if (loader != null) {
            try {
                loader.close();
            } catch (Exception ignored) {
            }
        }
    }
}
