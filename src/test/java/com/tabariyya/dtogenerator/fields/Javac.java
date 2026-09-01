package com.tabariyya.dtogenerator.fields;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
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

    static Compilation compile(File classesDir, JavaSource... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null);

        List<String> options = new ArrayList<>(Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-d", classesDir.getAbsolutePath(),
                "-processor", FieldsProcessor.class.getName()));

        boolean success = compiler
                .getTask(null, files, diagnostics, options, null, Arrays.asList((JavaFileObject[]) sources))
                .call();

        return new Compilation(success, diagnostics.getDiagnostics());
    }
}
