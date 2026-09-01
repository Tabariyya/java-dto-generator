package com.tabariyya.dtogenerator.fields;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What {@link FieldPath} accepts as a value, and what it refuses. */
class FieldPathValidatorTest {

    @TempDir
    File classes;

    @Test
    void acceptsAPathNamingARealField() {
        assertCompiles(use("@Marker(anyField = {User.ID})"));
    }

    @Test
    void rejectsAStringThatIsNotAPath() {
        assertFails(use("@Marker(anyField = {\"totally.made.up\"})"), FieldConstants.notAFieldMessage("totally.made.up"));
    }

    @Test
    void rejectsAPathWhoseOwnerHasNoSuchField() {
        assertFails(use("@Marker(anyField = {\"demo.User#missing\"})"), FieldConstants.notAFieldMessage("demo.User#missing"));
    }

    @Test
    void acceptsAPathNamingTheFixedOwner() {
        assertCompiles(use("@Marker(userField = {User.PASSWORD})"));
    }

    @Test
    void rejectsAPathNamingAnotherClass() {
        assertFails(
                use("@Marker(userField = {Account.DATE})"),
                FieldConstants.wrongOwnerMessage("demo.Account#date", "demo.Account", "demo.User"));
    }

    @Test
    void rejectsASubclassPathWhereTheSuperclassIsExpected() {
        assertFails(
                use("@Marker(userField = {Engineer.ID})"),
                FieldConstants.wrongOwnerMessage("demo.Engineer#id", "demo.Engineer", "demo.User"));
    }

    @Test
    void acceptsAPathNamingTheAnnotatedMethodsReturnType() {
        assertCompiles(useOnMethod("User", "@Marker(ownField = {User.PASSWORD})"));
    }

    @Test
    void rejectsAPathNamingAnythingButTheAnnotatedMethodsReturnType() {
        assertFails(
                useOnMethod("User", "@Marker(ownField = {Account.DATE})"),
                FieldConstants.wrongOwnerMessage("demo.Account#date", "demo.Account", "demo.User"));
    }

    @Test
    void theSameMemberFollowsWhicheverReturnTypeItIsUsedOn() {
        assertCompiles(
                Javac.source(
                        "demo.Use",
                        "package demo;",
                        "public class Use {",
                        "    @Marker(ownField = {User.PASSWORD}) User user() { return null; }",
                        "    @Marker(ownField = {Account.DATE}) Account account() { return null; }",
                        "}"));
    }

    @Test
    void rejectsReturnTypeModeWhereThereIsNoReturnType() {
        assertFails(
                Javac.source(
                        "demo.Use",
                        "package demo;",
                        "@Marker(ownField = {User.PASSWORD})",
                        "public class Use {}"),
                FieldConstants.notOnAMethodMessage("ownField"));
    }

    @Test
    void rejectsReturnTypeModeOnAReturnTypeThatIsNotAClass() {
        assertFails(
                Javac.source(
                        "demo.Use",
                        "package demo;",
                        "public class Use {",
                        "    @Marker(ownField = {User.PASSWORD}) void nothing() {}",
                        "}"),
                FieldConstants.unusableReturnTypeMessage("ownField", "void"));
    }

    @Test
    void rejectsAMemberThatIsNeitherStringNorStringArray() {
        assertFails(
                Javac.source(
                        "demo.Bad",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.FieldPath;",
                        "public @interface Bad {",
                        "    @FieldPath int count() default 0;",
                        "}"),
                FieldConstants.unsupportedTypeMessage("int"));
    }

    @Test
    void rejectsUseOutsideAnAnnotationType() {
        assertFails(
                Javac.source(
                        "demo.Bad",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.FieldPath;",
                        "public class Bad {",
                        "    @FieldPath String path() { return null; }",
                        "}"),
                FieldConstants.notAnAnnotationMemberMessage());
    }

    @Test
    void rejectsNamingAnOwnerTwice() {
        assertFails(
                Javac.source(
                        "demo.Bad",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.fields.FieldPath;",
                        "public @interface Bad {",
                        "    @FieldPath(value = User.class, returnType = true) String[] both() default {};",
                        "}"),
                FieldConstants.conflictingOwnerMessage());
    }

    @Test
    void anUnconstrainedMemberTakesFieldsOfAnyClass() {
        assertCompiles(use("@Marker(anyField = {User.ID, Account.DATE, Engineer.ID})"));
    }

    private Javac.Source use(String annotation) {
        return Javac.source("demo.Use", "package demo;", annotation, "public class Use {}");
    }

    private Javac.Source useOnMethod(String returnType, String annotation) {
        return Javac.source(
                "demo.Use",
                "package demo;",
                "public class Use {",
                "    " + annotation + " " + returnType + " get() { return null; }",
                "}");
    }

    private void assertCompiles(Javac.Source use) {
        Javac.Result result = compile(use);
        assertTrue(result.succeeded(), result.toString());
    }

    private void assertFails(Javac.Source use, String message) {
        Javac.Result result = compile(use);
        List<String> errors = result.errors();
        for (String error : errors) {
            if (error.contains(message)) {
                return;
            }
        }
        throw new AssertionError("expected an error containing:\n  " + message + "\nbut got:\n  " + errors);
    }

    private Javac.Result compile(Javac.Source use) {
        return Javac.compile(classes, marker(), user(), engineer(), account(), use);
    }

    private static Javac.Source marker() {
        return Javac.source(
                "demo.Marker",
                "package demo;",
                "import com.tabariyya.dtogenerator.fields.FieldPath;",
                "public @interface Marker {",
                "    @FieldPath String[] anyField() default {};",
                "    @FieldPath(User.class) String[] userField() default {};",
                "    @FieldPath(returnType = true) String[] ownField() default {};",
                "}");
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

    private static Javac.Source engineer() {
        return Javac.source(
                "demo.Engineer",
                "package demo;",
                "import com.tabariyya.dtogenerator.fields.Fields;",
                "@Fields public class Engineer extends User {",
                "    private String emailAddress;",
                "}");
    }

    private static Javac.Source account() {
        return Javac.source(
                "demo.Account",
                "package demo;",
                "import com.tabariyya.dtogenerator.fields.Fields;",
                "@Fields public class Account {",
                "    private String date;",
                "}");
    }
}
