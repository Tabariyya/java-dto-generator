package com.tabariyya.dtogenerator.fields;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What {@link FieldPath} accepts as a value, and what it refuses. */
class FieldPathValidatorTest {

    private static final String PACKAGE = "package demo;";
    private static final String IMPORT_FIELDS = "import com.tabariyya.dtogenerator.fields.Fields;";
    private static final String IMPORT_FIELD_PATH = "import com.tabariyya.dtogenerator.fields.FieldPath;";

    private static final String USER = "demo.User";
    private static final String ENGINEER = "demo.Engineer";
    private static final String ACCOUNT = "demo.Account";

    private static final JavaSource MARKER = JavaSource.of(
            "demo.Marker", PACKAGE, IMPORT_FIELD_PATH,
            "public @interface Marker {",
            "    @FieldPath String[] anyField() default {};",
            "    @FieldPath(User.class) String[] userField() default {};",
            "    @FieldPath(returnType = true) String[] ownField() default {};",
            "}");

    private static final JavaSource USER_SOURCE = JavaSource.of(
            USER, PACKAGE, IMPORT_FIELDS,
            "@Fields public class User {", "    private String id;", "    private String password;", "}");

    private static final JavaSource ENGINEER_SOURCE = JavaSource.of(
            ENGINEER, PACKAGE, IMPORT_FIELDS,
            "@Fields public class Engineer extends User {", "    private String emailAddress;", "}");

    private static final JavaSource ACCOUNT_SOURCE = JavaSource.of(
            ACCOUNT, PACKAGE, IMPORT_FIELDS, "@Fields public class Account {", "    private String date;", "}");

    @TempDir
    File classes;

    @Test
    void acceptsAPathNamingARealField() {
        assertCompiles(use("@Marker(anyField = {User.ID})"));
    }

    @Test
    void rejectsAStringThatIsNotAPath() {
        assertFails(
                use("@Marker(anyField = {\"totally.made.up\"})"),
                FieldConstants.notAFieldMessage("totally.made.up"));
    }

    @Test
    void rejectsAPathWhoseOwnerHasNoSuchField() {
        assertFails(
                use("@Marker(anyField = {\"demo.User#missing\"})"),
                FieldConstants.notAFieldMessage("demo.User#missing"));
    }

    @Test
    void acceptsAPathNamingTheFixedOwner() {
        assertCompiles(use("@Marker(userField = {User.PASSWORD})"));
    }

    @Test
    void rejectsAPathNamingAnotherClass() {
        assertFails(
                use("@Marker(userField = {Account.DATE})"),
                FieldConstants.wrongOwnerMessage("demo.Account#date", ACCOUNT, USER));
    }

    @Test
    void rejectsASubclassPathWhereTheSuperclassIsExpected() {
        assertFails(
                use("@Marker(userField = {Engineer.ID})"),
                FieldConstants.wrongOwnerMessage("demo.Engineer#id", ENGINEER, USER));
    }

    @Test
    void acceptsAPathNamingTheAnnotatedMethodsReturnType() {
        assertCompiles(useOnMethod("@Marker(ownField = {User.PASSWORD})"));
    }

    @Test
    void rejectsAPathNamingAnythingButTheAnnotatedMethodsReturnType() {
        assertFails(
                useOnMethod("@Marker(ownField = {Account.DATE})"),
                FieldConstants.wrongOwnerMessage("demo.Account#date", ACCOUNT, USER));
    }

    @Test
    void theSameMemberFollowsWhicheverReturnTypeItIsUsedOn() {
        assertCompiles(JavaSource.of(
                "demo.Use", PACKAGE,
                "public class Use {",
                "    @Marker(ownField = {User.PASSWORD}) User user() { return null; }",
                "    @Marker(ownField = {Account.DATE}) Account account() { return null; }",
                "}"));
    }

    @Test
    void rejectsReturnTypeModeWhereThereIsNoReturnType() {
        assertFails(
                JavaSource.of("demo.Use", PACKAGE, "@Marker(ownField = {User.PASSWORD})", "public class Use {}"),
                FieldConstants.notOnAMethodMessage("ownField"));
    }

    @Test
    void rejectsReturnTypeModeOnAReturnTypeThatIsNotAClass() {
        assertFails(
                JavaSource.of(
                        "demo.Use", PACKAGE,
                        "public class Use {",
                        "    @Marker(ownField = {User.PASSWORD}) void nothing() {}",
                        "}"),
                FieldConstants.unusableReturnTypeMessage("ownField", "void"));
    }

    @Test
    void rejectsAMemberThatIsNeitherStringNorStringArray() {
        assertFails(
                JavaSource.of(
                        "demo.Bad", PACKAGE, IMPORT_FIELD_PATH,
                        "public @interface Bad {", "    @FieldPath int count() default 0;", "}"),
                FieldConstants.unsupportedTypeMessage("int"));
    }

    @Test
    void rejectsUseOutsideAnAnnotationType() {
        assertFails(
                JavaSource.of(
                        "demo.Bad", PACKAGE, IMPORT_FIELD_PATH,
                        "public class Bad {", "    @FieldPath String path() { return null; }", "}"),
                FieldConstants.notAnAnnotationMemberMessage());
    }

    @Test
    void rejectsNamingAnOwnerTwice() {
        assertFails(
                JavaSource.of(
                        "demo.Bad", PACKAGE, IMPORT_FIELD_PATH,
                        "public @interface Bad {",
                        "    @FieldPath(value = User.class, returnType = true) String[] both() default {};",
                        "}"),
                FieldConstants.conflictingOwnerMessage());
    }

    @Test
    void anUnconstrainedMemberTakesFieldsOfAnyClass() {
        assertCompiles(use("@Marker(anyField = {User.ID, Account.DATE, Engineer.ID})"));
    }

    private static JavaSource use(String annotation) {
        return JavaSource.of("demo.Use", PACKAGE, annotation, "public class Use {}");
    }

    private static JavaSource useOnMethod(String annotation) {
        return JavaSource.of(
                "demo.Use", PACKAGE,
                "public class Use {",
                "    " + annotation + " User get() { return null; }",
                "}");
    }

    private void assertCompiles(JavaSource use) {
        Compilation result = compile(use);
        assertTrue(result.succeeded(), result.toString());
    }

    private void assertFails(JavaSource use, String message) {
        List<String> errors = compile(use).errors();
        for (String error : errors) {
            if (error.contains(message)) {
                return;
            }
        }
        throw new AssertionError("expected an error containing:\n  " + message + "\nbut got:\n  " + errors);
    }

    private Compilation compile(JavaSource use) {
        return Javac.compile(classes, MARKER, USER_SOURCE, ENGINEER_SOURCE, ACCOUNT_SOURCE, use);
    }
}
