package com.tabariyya.dtogenerator.fields;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What {@link Fields} injects, and what it refuses to inject. */
class FieldsProcessorTest {

    @TempDir
    File classes;

    @Test
    void injectsAConstantPerInstanceField() {
        Javac.Result result = Javac.compile(
                classes,
                Javac.source(
                        "demo.User",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.Fields;",
                        "@Fields public class User {",
                        "    private String id;",
                        "    private String firstName;",
                        "    private String userID;",
                        "    private String htmlURLParser;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertEquals("demo.User#id", result.constant("demo.User", "ID"));
        assertEquals("demo.User#firstName", result.constant("demo.User", "FIRST_NAME"));
        assertEquals("demo.User#userID", result.constant("demo.User", "USER_ID"));
        assertEquals("demo.User#htmlURLParser", result.constant("demo.User", "HTML_URL_PARSER"));
    }

    @Test
    void theConstantsAreUsableAsAnnotationArguments() {
        Javac.Result result = Javac.compile(
                classes,
                user(),
                Javac.source(
                        "demo.Marker",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.FieldPath;",
                        "public @interface Marker {",
                        "    @FieldPath String[] fields() default {};",
                        "}"),
                Javac.source(
                        "demo.Use",
                        "package demo;",
                        "@Marker(fields = {User.ID, User.PASSWORD})",
                        "public class Use {}"));

        assertTrue(result.succeeded(), result.toString());
    }

    @Test
    void aSubclassGetsItsOwnConstantForAnInheritedField() {
        Javac.Result result = Javac.compile(
                classes,
                user(),
                Javac.source(
                        "demo.Engineer",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.Fields;",
                        "@Fields public class Engineer extends User {",
                        "    private String emailAddress;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertEquals("demo.Engineer#id", result.constant("demo.Engineer", "ID"));
        assertEquals("demo.Engineer#emailAddress", result.constant("demo.Engineer", "EMAIL_ADDRESS"));
        assertEquals("demo.User#id", result.constant("demo.User", "ID"));
    }

    @Test
    void theSupertypeDoesNotHaveToBeAnnotated() {
        Javac.Result result = Javac.compile(
                classes,
                Javac.source("demo.Base", "package demo;", "public class Base {", "    protected String id;", "}"),
                Javac.source(
                        "demo.Derived",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.Fields;",
                        "@Fields public class Derived extends Base {}"));

        assertTrue(result.succeeded(), result.toString());
        assertEquals("demo.Derived#id", result.constant("demo.Derived", "ID"));
    }

    @Test
    void theWalkStopsBeforeJdkClasses() {
        Javac.Result result = Javac.compile(
                classes,
                Javac.source(
                        "demo.Failure",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.Fields;",
                        "@Fields public class Failure extends RuntimeException {",
                        "    private String field;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertEquals("demo.Failure#field", result.constant("demo.Failure", "FIELD"));
        assertNull(result.constant("demo.Failure", "DETAIL_MESSAGE"));
    }

    @Test
    void staticFieldsAreSkippedWithoutAWarning() {
        Javac.Result result = Javac.compile(
                classes,
                Javac.source(
                        "demo.WithStatics",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.Fields;",
                        "@Fields public class WithStatics {",
                        "    static String shared;",
                        "    private String kept;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertEquals("demo.WithStatics#kept", result.constant("demo.WithStatics", "KEPT"));
        assertNull(result.constant("demo.WithStatics", "SHARED"));
        assertEquals(0, result.warnings().size(), result.toString());
    }

    @Test
    void aFieldAlreadyNamedLikeAConstantIsOmittedWithAWarning() {
        Javac.Result result = Javac.compile(
                classes,
                Javac.source(
                        "demo.Shouty",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.Fields;",
                        "@Fields public class Shouty {",
                        "    private String FOO;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertWarned(result, FieldConstants.nameIsAlreadyConstantMessage("FOO"));
    }

    @Test
    void aHandWrittenConstantIsKeptAndTheGeneratedOneOmittedWithAWarning() {
        Javac.Result result = Javac.compile(
                classes,
                Javac.source(
                        "demo.Overriding",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.Fields;",
                        "@Fields public class Overriding {",
                        "    public static final String ID = \"custom\";",
                        "    private String id;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertEquals("custom", result.constant("demo.Overriding", "ID"));
        assertWarned(result, FieldConstants.nameTakenMessage("ID", "id"));
    }

    @Test
    void twoFieldsMappingToOneConstantGetNeitherOfThem() {
        Javac.Result result = Javac.compile(
                classes,
                Javac.source(
                        "demo.Colliding",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.Fields;",
                        "@Fields public class Colliding {",
                        "    private String firstName;",
                        "    private String first_name;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertNull(result.constant("demo.Colliding", "FIRST_NAME"));
        assertWarned(result, "no constant is generated for FIRST_NAME: fields firstName, first_name all map to it");
    }

    @Test
    void interfacesAreRejectedWithAWarning() {
        Javac.Result result = Javac.compile(
                classes,
                Javac.source(
                        "demo.Contract",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.Fields;",
                        "@Fields public interface Contract {",
                        "    String name();",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertWarned(result, FieldConstants.notSupportedOnInterfacesMessage());
    }

    @Test
    void enumsAreRejectedWithAWarning() {
        Javac.Result result = Javac.compile(
                classes,
                Javac.source(
                        "demo.Grade",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.Fields;",
                        "@Fields public enum Grade {",
                        "    A, B;",
                        "    private String label;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertNull(result.constant("demo.Grade", "LABEL"));
        assertWarned(result, FieldConstants.notSupportedOnEnumsMessage());
    }

    @Test
    void noEdgeCaseEverFailsTheBuild() {
        Javac.Result result = Javac.compile(
                classes,
                Javac.source(
                        "demo.EveryProblemAtOnce",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.Fields;",
                        "@Fields public class EveryProblemAtOnce {",
                        "    public static final String KEPT = \"mine\";",
                        "    private String FOO;",
                        "    private String kept;",
                        "    private String firstName;",
                        "    private String first_name;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertEquals(0, result.errors().size(), result.toString());
        assertEquals(3, result.warnings().size(), result.toString());
    }

    private static Javac.Source user() {
        return Javac.source(
                "demo.User",
                "package demo;",
                "import com.tabariyya.dtogenerator.fields.Fields;",
                "@Fields public class User {",
                "    private String id;",
                "    private String password;",
                "}");
    }

    private static void assertWarned(Javac.Result result, String message) {
        List<String> warnings = result.warnings();
        for (String warning : warnings) {
            if (warning.contains(message)) {
                return;
            }
        }
        throw new AssertionError("expected a warning containing:\n  " + message + "\nbut got:\n  " + warnings);
    }
}
