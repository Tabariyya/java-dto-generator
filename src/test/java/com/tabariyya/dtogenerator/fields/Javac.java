package com.tabariyya.dtogenerator.fields;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Runs the real compiler over in-memory sources with {@link FieldsProcessor} attached.
 *
 * <p>The tests around it are the only place the whole chain is exercised the way a consumer meets
 * it: constants injected into a syntax tree, then read back from the class file, then annotation
 * arguments checked once analysis has run. Nothing shorter than an actual compilation reproduces the
 * ordering these depend on.
 */
final class Javac {

    private Javac() {}

    static Source source(String qualifiedName, String... lines) {
        return new Source(qualifiedName, join(lines));
    }

    static Result compile(File classesDir, Source... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null);

        List<String> options = new ArrayList<String>(Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-d", classesDir.getAbsolutePath(),
                "-processor", FieldsProcessor.class.getName()));

        boolean success = compiler
                .getTask(null, files, diagnostics, options, null, Arrays.asList((JavaFileObject[]) sources))
                .call();

        return new Result(success, diagnostics.getDiagnostics(), classesDir);
    }

    static final class Source extends SimpleJavaFileObject {

        private final String content;

        Source(String qualifiedName, String content) {
            super(URI.create("string:///" + qualifiedName.replace('.', '/') + ".java"), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }

    static final class Result {

        private final boolean success;
        private final List<Diagnostic<? extends JavaFileObject>> diagnostics;
        private final File classesDir;

        Result(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics, File classesDir) {
            this.success = success;
            this.diagnostics = diagnostics;
            this.classesDir = classesDir;
        }

        boolean succeeded() {
            return success;
        }

        List<String> errors() {
            return messagesOf(Diagnostic.Kind.ERROR);
        }

        List<String> warnings() {
            return messagesOf(Diagnostic.Kind.WARNING);
        }

        /** The value the injected constant actually holds, read from the compiled class file. */
        String constant(String qualifiedClassName, String constantName) {
            URLClassLoader loader = null;
            try {
                loader = new URLClassLoader(
                        new URL[] {classesDir.toURI().toURL()}, Javac.class.getClassLoader());
                Object value = loader.loadClass(qualifiedClassName).getField(constantName).get(null);
                return String.valueOf(value);
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
                    // Nothing useful to do about a class loader that will be collected anyway.
                }
            }
        }

        private List<String> messagesOf(Diagnostic.Kind kind) {
            List<String> messages = new ArrayList<String>();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
                if (diagnostic.getKind() == kind) {
                    messages.add(diagnostic.getMessage(Locale.ENGLISH));
                }
            }
            return messages;
        }

        @Override
        public String toString() {
            List<String> all = new ArrayList<String>();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
                all.add(diagnostic.getKind() + ": " + diagnostic.getMessage(Locale.ENGLISH));
            }
            return all.toString();
        }
    }

    private static String join(String[] lines) {
        StringBuilder source = new StringBuilder();
        for (String line : lines) {
            source.append(line).append('\n');
        }
        return source.toString();
    }
}
