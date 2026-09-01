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
 * Runs the real compiler over in-memory sources. Nothing shorter reproduces the ordering these tests
 * depend on: injection, then attribution, then the checks that run after analysis.
 */
public final class Javac {

    private Javac() {}

    public static Compilation compile(File classesDir, JavaSource... sources) {
        return compileWith(classesDir, FieldsProcessor.class.getName(), sources);
    }

    public static Compilation compileWith(File classesDir, String processor, JavaSource... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null);

        List<String> options = new ArrayList<>(Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-d", classesDir.getAbsolutePath(),
                "-processor", processor));

        boolean success = compiler
                .getTask(null, files, diagnostics, options, null, Arrays.asList((JavaFileObject[]) sources))
                .call();

        return new Compilation(success, diagnostics.getDiagnostics());
    }
}
