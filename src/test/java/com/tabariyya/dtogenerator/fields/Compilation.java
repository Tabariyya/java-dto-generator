package com.tabariyya.dtogenerator.fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

public final class Compilation {

    private final boolean success;
    private final List<Diagnostic<? extends JavaFileObject>> diagnostics;

    Compilation(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        this.success = success;
        this.diagnostics = diagnostics;
    }

    public boolean succeeded() {
        return success;
    }

    public List<String> errors() {
        return messagesOf(Diagnostic.Kind.ERROR);
    }

    public List<String> warnings() {
        return messagesOf(Diagnostic.Kind.WARNING);
    }

    private List<String> messagesOf(Diagnostic.Kind kind) {
        List<String> messages = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            if (diagnostic.getKind() == kind) {
                messages.add(diagnostic.getMessage(Locale.ENGLISH));
            }
        }
        return messages;
    }

    /** Every diagnostic, so a failing assertion says what the compiler actually reported. */
    @Override
    public String toString() {
        List<String> all = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            all.add(diagnostic.getKind() + ": " + diagnostic.getMessage(Locale.ENGLISH));
        }
        return all.toString();
    }
}
