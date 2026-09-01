package com.tabariyya.dtogenerator.idea;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.util.List;

public class FieldsAnnotatorTest extends LightJavaCodeInsightFixtureTestCase {

    private static final String NOT_ON_ENUMS = "@Fields is not supported on enums";

    private static final String NOT_ON_INTERFACES =
            "@Fields is not supported on interfaces, which cannot declare instance fields";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addClass("package com.tabariyya.dtogenerator.fields; public @interface Fields {}");
    }

    public void testFlagsFieldsOnAnInterface() {
        assertEquals(
                List.of(NOT_ON_INTERFACES),
                problemsIn("@com.tabariyya.dtogenerator.fields.Fields interface I { }"));
    }

    public void testFlagsFieldsOnAnAnnotationType() {
        assertEquals(
                List.of(NOT_ON_INTERFACES),
                problemsIn("@com.tabariyya.dtogenerator.fields.Fields @interface A { }"));
    }

    public void testAcceptsFieldsOnAClass() {
        assertEquals(List.of(), problemsIn("@com.tabariyya.dtogenerator.fields.Fields class C { private int x; }"));
    }

    public void testIgnoresAnInterfaceWithoutTheAnnotation() {
        assertEquals(List.of(), problemsIn("interface Plain { }"));
    }

    public void testWarnsAboutAConstantTwoFieldsWouldShare() {
        assertEquals(
                List.of("no constant is generated for FIRST_NAME: fields firstName, first_name all map to it"),
                problemsIn("@com.tabariyya.dtogenerator.fields.Fields class C {"
                        + " private String firstName; private String first_name; }"));
    }

    public void testInjectsNoConstantTwoFieldsWouldShare() {
        myFixture.addClass("package a; @com.tabariyya.dtogenerator.fields.Fields"
                + " public class Collide { private String firstName; private String first_name; }");

        assertEquals(
                0,
                java.util.Arrays.stream(myFixture.findClass("a.Collide").getFields())
                        .filter(f -> "FIRST_NAME".equals(f.getName()))
                        .count());
    }

    public void testWarnsWhenAFieldNameIsAlreadyInConstantForm() {
        assertEquals(
                List.of("no constant is generated for field FOO: its name is already in constant form"),
                problemsIn("@com.tabariyya.dtogenerator.fields.Fields class C { private String FOO; }"));
    }

    public void testWarnsWhenTheConstantNameIsTakenByAnotherField() {
        assertEquals(
                List.of("no constant is generated for field ID: its name is already in constant form",
                        "no constant is generated for field id: ID is already a field of this class"),
                problemsIn("@com.tabariyya.dtogenerator.fields.Fields class C {"
                        + " private String id; private String ID; }"));
    }

    public void testWarnsWhenAHandWrittenConstantTakesTheName() {
        assertEquals(
                List.of("no constant is generated for field tenant: TENANT is already a field of this class"),
                problemsIn("@com.tabariyya.dtogenerator.fields.Fields class C {"
                        + " public static final String TENANT = \"custom\"; private String tenant; }"));
    }

    public void testFlagsFieldsOnAnEnum() {
        assertEquals(
                List.of(NOT_ON_ENUMS),
                problemsIn("@com.tabariyya.dtogenerator.fields.Fields enum E { A; private String label; }"));
    }

    private List<String> problemsIn(String declaration) {
        myFixture.configureByText("Subject.java", "package a;\n" + declaration);
        return myFixture.doHighlighting(HighlightSeverity.WARNING).stream()
                .map(HighlightInfo::getDescription)
                .filter(FieldsAnnotatorTest::isFieldsProblem)
                .sorted()
                .toList();
    }

    private static boolean isFieldsProblem(String description) {
        return description != null
                && (description.equals(NOT_ON_INTERFACES)
                        || description.equals(NOT_ON_ENUMS)
                        || description.contains("no constant is generated for"));
    }
}
