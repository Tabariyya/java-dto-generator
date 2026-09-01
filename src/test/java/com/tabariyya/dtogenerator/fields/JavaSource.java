package com.tabariyya.dtogenerator.fields;

import java.net.URI;
import javax.tools.SimpleJavaFileObject;

/** A source file held in memory, written a line at a time so the tests read like Java. */
public final class JavaSource extends SimpleJavaFileObject {

    private final String content;

    private JavaSource(String qualifiedName, String content) {
        super(URI.create("string:///" + qualifiedName.replace('.', '/') + ".java"), Kind.SOURCE);
        this.content = content;
    }

    public static JavaSource of(String qualifiedName, String... lines) {
        StringBuilder source = new StringBuilder();
        for (String line : lines) {
            source.append(line).append('\n');
        }
        return new JavaSource(qualifiedName, source.toString());
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return content;
    }
}
