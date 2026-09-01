package com.tabariyya.dtogenerator.idea;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.util.List;

public class FieldsAugmentProviderTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addClass("package com.tabariyya.dtogenerator.fields; public @interface Fields {}");
    }

    public void testInjectsAConstantPerInstanceField() {
        PsiClass account = annotatedClass("private String firstName; private String iban;");

        assertConstant(account, "FIRST_NAME", "a.Account#firstName");
        assertConstant(account, "IBAN", "a.Account#iban");
    }

    public void testKeepsAConstantThatIsAlreadyDeclared() {
        PsiClass account = annotatedClass("public static final String ID = \"custom\"; private String id;");

        PsiField[] declared = account.getFields();
        assertEquals(1, java.util.Arrays.stream(declared).filter(f -> "ID".equals(f.getName())).count());
        assertFalse(account.findFieldByName("ID", false) instanceof FieldConstant);
    }

    public void testIgnoresStaticFields() {
        PsiClass account = annotatedClass("private static final long serialVersionUID = 1L;");

        assertNull(account.findFieldByName("SERIAL_VERSION_UID", false));
    }

    public void testInjectsFieldsOfASupertypeThatIsNotAnnotated() {
        myFixture.addClass("package a; public class PlainBase { private String openedAt; }");
        myFixture.addClass("package a; @com.tabariyya.dtogenerator.fields.Fields"
                + " public class Ledger extends PlainBase { private String balance; }");

        assertConstant(myFixture.findClass("a.Ledger"), "OPENED_AT", "a.Ledger#openedAt");
        assertConstant(myFixture.findClass("a.Ledger"), "BALANCE", "a.Ledger#balance");
    }

    public void testOmitsAConstantForAFieldAlreadyNamedLikeOne() {
        myFixture.addClass("package a; public class CapsBase { protected String CODE; }");
        myFixture.addClass("package a; @com.tabariyya.dtogenerator.fields.Fields"
                + " public class Caps extends CapsBase { private String SKU; private String name; }");

        PsiClass caps = myFixture.findClass("a.Caps");
        assertConstant(caps, "NAME", "a.Caps#name");
        assertNull(caps.findFieldByName("CODE", false));
        assertEquals(1, java.util.Arrays.stream(caps.getFields())
                .filter(f -> "SKU".equals(f.getName()))
                .count());
    }

    public void testAppliesEveryOmissionRuleAtOnce() {
        myFixture.addClass("package a; public class DiffBase { protected String CODE; private String tenant; }");
        myFixture.addClass("package a; @com.tabariyya.dtogenerator.fields.Fields public class DiffSub extends DiffBase {"
                + " private static final long serialVersionUID = 1L;"
                + " public static final String TENANT = \"custom\";"
                + " private String FOO;"
                + " private String userID;"
                + " private String firstName; }");

        PsiClass sub = myFixture.findClass("a.DiffSub");
        assertConstant(sub, "FIRST_NAME", "a.DiffSub#firstName");
        assertConstant(sub, "USER_ID", "a.DiffSub#userID");
        assertEquals(
                List.of("FIRST_NAME", "FOO", "TENANT", "USER_ID", "firstName", "serialVersionUID", "userID"),
                java.util.Arrays.stream(sub.getFields()).map(PsiField::getName).sorted().toList());
    }

    public void testInjectsNothingIntoAnEnum() {
        myFixture.addClass("package a; @com.tabariyya.dtogenerator.fields.Fields"
                + " public enum Status { ACTIVE; private String label; }");

        assertNull(myFixture.findClass("a.Status").findFieldByName("LABEL", false));
    }

    public void testStopsTheSupertypeWalkAtJdkTypes() {
        myFixture.addClass("package a; @com.tabariyya.dtogenerator.fields.Fields"
                + " public class JdkChild extends java.util.ArrayList<String> { private String own; }");

        PsiClass child = myFixture.findClass("a.JdkChild");
        assertConstant(child, "OWN", "a.JdkChild#own");
        assertNull(child.findFieldByName("SIZE", false));
        assertNull(child.findFieldByName("ELEMENT_DATA", false));
    }

    public void testIgnoresClassesWithoutTheAnnotation() {
        myFixture.addClass("package a; public class Plain { private String username; }");

        assertNull(myFixture.findClass("a.Plain").findFieldByName("USERNAME", false));
    }

    private PsiClass annotatedClass(String body) {
        myFixture.addClass(
                "package a; @com.tabariyya.dtogenerator.fields.Fields public class Account { " + body + " }");
        return myFixture.findClass("a.Account");
    }

    private void assertConstant(PsiClass owner, String name, String value) {
        PsiField constant = owner.findFieldByName(name, false);
        assertNotNull(name + " was not injected", constant);
        assertTrue(constant.hasModifierProperty(PsiModifier.PUBLIC));
        assertTrue(constant.hasModifierProperty(PsiModifier.STATIC));
        assertTrue(constant.hasModifierProperty(PsiModifier.FINAL));
        assertEquals("java.lang.String", constant.getType().getCanonicalText());
        assertEquals(value, constant.computeConstantValue());
    }
}
