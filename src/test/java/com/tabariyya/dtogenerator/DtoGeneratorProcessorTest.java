package com.tabariyya.dtogenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tabariyya.dtogenerator.fields.Compilation;
import com.tabariyya.dtogenerator.fields.JavaSource;
import com.tabariyya.dtogenerator.fields.Javac;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Java 8 and Java 9 print an annotated type differently, and the generator has to produce the same
 * source either way. The strings below are what each compiler actually hands a processor.
 */
class DtoGeneratorProcessorTest {

    @TempDir
    File classes;

    @Test
    void unwrapsTheNotationJava8UsesForAnAnnotatedType() {
        assertEquals(
                "java.lang.String",
                DtoGeneratorProcessor.cleanTypeSignature(
                        "(@jakarta.validation.constraints.NotBlank :: java.lang.String)"));
    }

    @Test
    void stripsTheAnnotationJava9AndLaterPrintsAsAPrefix() {
        assertEquals(
                "java.lang.String",
                DtoGeneratorProcessor.cleanTypeSignature(
                        "@jakarta.validation.constraints.NotBlank java.lang.String"));
    }

    @Test
    void unwrapsAnAnnotatedTypeArgumentInsideAnAnnotatedType() {
        assertEquals(
                "java.util.List<java.lang.String>",
                DtoGeneratorProcessor.cleanTypeSignature(
                        "(@a.A :: java.util.List<(@b.B :: java.lang.String)>)"));
    }

    @Test
    void unwrapsAnAnnotationThatCarriesArguments() {
        assertEquals(
                "java.lang.String",
                DtoGeneratorProcessor.cleanTypeSignature(
                        "(@jakarta.validation.constraints.Size(min=1) :: java.lang.String)"));
    }

    @Test
    void leavesAnUnannotatedTypeExactlyAsItIs() {
        assertEquals("java.lang.String", DtoGeneratorProcessor.cleanTypeSignature("java.lang.String"));
        assertEquals(
                "java.util.Map<java.lang.String,java.lang.Integer>",
                DtoGeneratorProcessor.cleanTypeSignature("java.util.Map<java.lang.String,java.lang.Integer>"));
    }

    /** Reproduces only on Java 8, which is the compiler this library's CI uses. */
    @Test
    void generatesCompilableCodeForAFieldCarryingATypeAnnotation() {
        Compilation result = Javac.compileWith(
                classes,
                DtoGeneratorProcessor.class.getName(),
                JavaSource.of(
                        "demo.Tagged",
                        "package demo;",
                        "import java.lang.annotation.ElementType;",
                        "import java.lang.annotation.Target;",
                        "@Target({ElementType.FIELD, ElementType.TYPE_USE})",
                        "public @interface Tagged {}"),
                JavaSource.of(
                        "demo.Model",
                        "package demo;",
                        "public class Model {",
                        "    @Tagged private String name;",
                        "    private java.util.List<String> tags;",
                        "}"),
                JavaSource.of(
                        "demo.Service",
                        "package demo;",
                        "import com.tabariyya.dtogenerator.annotations.GenerateDto;",
                        "public interface Service {",
                        "    @GenerateDto",
                        "    Model modelView();",
                        "}"));

        assertTrue(result.succeeded(), result.toString());
    }
}
