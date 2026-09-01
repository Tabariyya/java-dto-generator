package com.tabariyya.dtogenerator.fields;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What {@link Fields} injects, and what it refuses to inject. */
class FieldsProcessorTest {

    private static final String PACKAGE = "package demo;";
    private static final String IMPORT = "import com.tabariyya.dtogenerator.fields.Fields;";

    private static final String USER = "demo.User";
    private static final String ENGINEER = "demo.Engineer";

    private static final JavaSource USER_SOURCE = JavaSource.of(
            USER, PACKAGE, IMPORT, "@Fields public class User {", "    private String id;",
            "    private String password;", "}");

    @TempDir
    File classes;

    private CompiledConstants constants;

    @BeforeEach
    void readConstantsFromTheCompiledClasses() {
        constants = new CompiledConstants(classes);
    }

    @Test
    void injectsAConstantPerInstanceField() {
        Compilation result = Javac.compile(
                classes,
                JavaSource.of(
                        USER, PACKAGE, IMPORT,
                        "@Fields public class User {",
                        "    private String id;",
                        "    private String firstName;",
                        "    private String userID;",
                        "    private String htmlURLParser;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertEquals("demo.User#id", constants.of(USER, "ID"));
        assertEquals("demo.User#firstName", constants.of(USER, "FIRST_NAME"));
        assertEquals("demo.User#userID", constants.of(USER, "USER_ID"));
        assertEquals("demo.User#htmlURLParser", constants.of(USER, "HTML_URL_PARSER"));
    }

    @Test
    void theConstantsAreUsableAsAnnotationArguments() {
        Compilation result = Javac.compile(
                classes,
                USER_SOURCE,
                JavaSource.of(
                        "demo.Marker", PACKAGE,
                        "import com.tabariyya.dtogenerator.fields.FieldPath;",
                        "public @interface Marker {",
                        "    @FieldPath String[] fields() default {};",
                        "}"),
                JavaSource.of("demo.Use", PACKAGE, "@Marker(fields = {User.ID, User.PASSWORD})", "public class Use {}"));

        assertTrue(result.succeeded(), result.toString());
    }

    @Test
    void aSubclassGetsItsOwnConstantForAnInheritedField() {
        Compilation result = Javac.compile(
                classes,
                USER_SOURCE,
                JavaSource.of(
                        ENGINEER, PACKAGE, IMPORT,
                        "@Fields public class Engineer extends User {",
                        "    private String emailAddress;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertEquals("demo.Engineer#id", constants.of(ENGINEER, "ID"));
        assertEquals("demo.Engineer#emailAddress", constants.of(ENGINEER, "EMAIL_ADDRESS"));
        assertEquals("demo.User#id", constants.of(USER, "ID"));
    }

    @Test
    void theSupertypeDoesNotHaveToBeAnnotated() {
        Compilation result = Javac.compile(
                classes,
                JavaSource.of("demo.Base", PACKAGE, "public class Base {", "    protected String id;", "}"),
                JavaSource.of("demo.Derived", PACKAGE, IMPORT, "@Fields public class Derived extends Base {}"));

        assertTrue(result.succeeded(), result.toString());
        assertEquals("demo.Derived#id", constants.of("demo.Derived", "ID"));
    }

    @Test
    void theWalkStopsBeforeJdkClasses() {
        Compilation result = Javac.compile(
                classes,
                JavaSource.of(
                        "demo.Failure", PACKAGE, IMPORT,
                        "@Fields public class Failure extends RuntimeException {",
                        "    private String field;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertEquals("demo.Failure#field", constants.of("demo.Failure", "FIELD"));
        assertNull(constants.of("demo.Failure", "DETAIL_MESSAGE"));
    }

    @Test
    void staticFieldsAreSkippedWithoutAWarning() {
        Compilation result = Javac.compile(
                classes,
                JavaSource.of(
                        "demo.WithStatics", PACKAGE, IMPORT,
                        "@Fields public class WithStatics {",
                        "    static String shared;",
                        "    private String kept;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertEquals("demo.WithStatics#kept", constants.of("demo.WithStatics", "KEPT"));
        assertNull(constants.of("demo.WithStatics", "SHARED"));
        assertEquals(Collections.<String>emptyList(), result.warnings());
    }

    @Test
    void aFieldAlreadyNamedLikeAConstantIsOmittedWithAWarning() {
        Compilation result = Javac.compile(
                classes,
                JavaSource.of("demo.Shouty", PACKAGE, IMPORT, "@Fields public class Shouty {", "    private String FOO;", "}"));

        assertTrue(result.succeeded(), result.toString());
        assertWarned(result, FieldConstants.nameIsAlreadyConstantMessage("FOO"));
    }

    @Test
    void aHandWrittenConstantIsKeptAndTheGeneratedOneOmittedWithAWarning() {
        Compilation result = Javac.compile(
                classes,
                JavaSource.of(
                        "demo.Overriding", PACKAGE, IMPORT,
                        "@Fields public class Overriding {",
                        "    public static final String ID = \"custom\";",
                        "    private String id;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertEquals("custom", constants.of("demo.Overriding", "ID"));
        assertWarned(result, FieldConstants.nameTakenMessage("ID", "id"));
    }

    @Test
    void twoFieldsMappingToOneConstantGetNeitherOfThem() {
        Compilation result = Javac.compile(
                classes,
                JavaSource.of(
                        "demo.Colliding", PACKAGE, IMPORT,
                        "@Fields public class Colliding {",
                        "    private String firstName;",
                        "    private String first_name;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertNull(constants.of("demo.Colliding", "FIRST_NAME"));
        assertWarned(result, FieldConstants.collisionMessage("FIRST_NAME", Arrays.asList("firstName", "first_name")));
    }

    @Test
    void interfacesAreRejectedWithAWarning() {
        Compilation result = Javac.compile(
                classes,
                JavaSource.of("demo.Contract", PACKAGE, IMPORT, "@Fields public interface Contract {", "    String name();", "}"));

        assertTrue(result.succeeded(), result.toString());
        assertWarned(result, FieldConstants.notSupportedOnInterfacesMessage());
    }

    @Test
    void enumsAreRejectedWithAWarning() {
        Compilation result = Javac.compile(
                classes,
                JavaSource.of(
                        "demo.Grade", PACKAGE, IMPORT,
                        "@Fields public enum Grade {",
                        "    A, B;",
                        "    private String label;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertNull(constants.of("demo.Grade", "LABEL"));
        assertWarned(result, FieldConstants.notSupportedOnEnumsMessage());
    }

    @Test
    void noEdgeCaseEverFailsTheBuild() {
        Compilation result = Javac.compile(
                classes,
                JavaSource.of(
                        "demo.EveryProblemAtOnce", PACKAGE, IMPORT,
                        "@Fields public class EveryProblemAtOnce {",
                        "    public static final String KEPT = \"mine\";",
                        "    private String FOO;",
                        "    private String kept;",
                        "    private String firstName;",
                        "    private String first_name;",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
        assertEquals(Collections.<String>emptyList(), result.errors());
        assertEquals(
                Arrays.asList(
                        FieldConstants.nameIsAlreadyConstantMessage("FOO"),
                        FieldConstants.nameTakenMessage("KEPT", "kept"),
                        FieldConstants.collisionMessage("FIRST_NAME", Arrays.asList("firstName", "first_name"))),
                result.warnings());
    }

    private static void assertWarned(Compilation result, String message) {
        List<String> warnings = result.warnings();
        for (String warning : warnings) {
            if (warning.contains(message)) {
                return;
            }
        }
        throw new AssertionError("expected a warning containing:\n  " + message + "\nbut got:\n  " + warnings);
    }
}
