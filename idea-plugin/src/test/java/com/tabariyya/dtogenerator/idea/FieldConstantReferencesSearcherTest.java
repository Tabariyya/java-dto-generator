package com.tabariyya.dtogenerator.idea;

import com.intellij.psi.PsiField;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class FieldConstantReferencesSearcherTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addClass("package com.tabariyya.dtogenerator.fields; public @interface Fields {}");
        myFixture.addClass("package a; @com.tabariyya.dtogenerator.fields.Fields public class User {"
                + " private String username; private String password; }");
    }

    public void testFindsUsagesOfTheConstantWhenSearchingTheField() {
        myFixture.addClass("package a; class Consumer { String used = User.USERNAME; }");

        assertEquals(1, usagesOf("username"));
    }

    public void testCountsNoUsagesForAFieldWhoseConstantIsUnused() {
        myFixture.addClass("package a; class Consumer { String used = User.USERNAME; }");

        assertEquals(0, usagesOf("password"));
    }

    public void testCountsEveryUsageOfTheConstant() {
        myFixture.addClass("package a; class A { String x = User.USERNAME; }");
        myFixture.addClass("package a; class B { String y = User.USERNAME; String z = User.USERNAME; }");

        assertEquals(3, usagesOf("username"));
    }

    public void testCountsUsagesOfASubclassConstantForAnInheritedField() {
        myFixture.addClass("package a; @com.tabariyya.dtogenerator.fields.Fields public class Admin extends User {"
                + " private String role; }");
        myFixture.addClass("package a; class C { String a = User.USERNAME; String b = Admin.USERNAME; }");

        assertEquals(2, usagesOf("username"));
    }

    private int usagesOf(String fieldName) {
        PsiField field = myFixture.findClass("a.User").findFieldByName(fieldName, false);
        assertNotNull(fieldName + " was not found", field);

        return ReferencesSearch.search(field).findAll().size();
    }
}
