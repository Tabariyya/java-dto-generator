package com.tabariyya.dtogenerator.idea;

import com.intellij.codeInsight.javadoc.JavaDocInfoGeneratorFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class FieldConstantDocumentationTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addClass("package com.tabariyya.dtogenerator.fields; public @interface Fields {}");
        myFixture.addClass("package a; @com.tabariyya.dtogenerator.fields.Fields public class User { private String username; }");
        myFixture.addClass("package a; @com.tabariyya.dtogenerator.fields.Fields public class Admin extends User { private String role; }");
    }

    public void testGeneratesDocumentationForAnInjectedConstant() {
        assertDocumented(myFixture.findClass("a.User"), "USERNAME", "a.User#username");
    }

    public void testGeneratesDocumentationForASubclassConstantOfAnInheritedField() {
        assertDocumented(myFixture.findClass("a.Admin"), "USERNAME", "a.Admin#username");
        assertDocumented(myFixture.findClass("a.Admin"), "ROLE", "a.Admin#role");
    }

    public void testTheInjectedConstantReportsAContainingFile() {
        PsiField constant = myFixture.findClass("a.User").findFieldByName("USERNAME", false);
        assertNotNull("USERNAME was not injected", constant);

        assertNotNull(constant.getContainingFile());
    }

    private void assertDocumented(PsiClass owner, String name, String expectedValue) {
        PsiField constant = owner.findFieldByName(name, true);
        assertNotNull(name + " was not resolved", constant);

        String doc = JavaDocInfoGeneratorFactory.create(getProject(), constant).generateDocInfo(null);

        assertNotNull("no documentation generated for " + name, doc);
        assertTrue("documentation did not contain the path: " + doc, doc.contains(expectedValue));
    }
}
