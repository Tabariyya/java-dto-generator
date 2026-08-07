package com.tabariyya.entityidgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compiles throwaway sources in-memory with the processor attached and asserts on what it emits.
 *
 * <p>The jakarta.persistence annotations are declared as stub sources rather than pulled in as a
 * dependency — the processor matches them by qualified name, so stubs exercise the real code path.
 */
class EntityIdProcessorTest {

    private static final String ENTITY_STUB = """
            package jakarta.persistence;

            public @interface Entity {}
            """;

    private static final String ID_STUB = """
            package jakarta.persistence;

            public @interface Id {}
            """;

    @TempDir
    Path outputDir;

    private List<JavaFileObject> sources;

    @BeforeEach
    void setUp() {
        sources = new ArrayList<>();
        addSource("jakarta.persistence.Entity", ENTITY_STUB);
        addSource("jakarta.persistence.Id", ID_STUB);
    }

    @Test
    void generatesIdRecordInTheEntityPackage() throws IOException {
        addSource("com.example.Follow", """
                package com.example;

                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import java.util.UUID;

                @Entity
                public class Follow {
                    @Id
                    private UUID id;
                }
                """);

        assertTrue(compile(), "compilation should succeed");

        String generated = readGenerated("com/example/FollowId.java");
        assertTrue(generated.contains("package com.example;"), "should share the entity's package");
        assertTrue(
                generated.contains("public record FollowId(UUID value) implements EntityId"),
                "should be a record wrapping UUID and implementing EntityId");
    }

    @Test
    void findsIdFieldRegardlessOfItsName() throws IOException {
        addSource("com.example.Address", """
                package com.example;

                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import java.util.UUID;

                @Entity
                public class Address {
                    @Id
                    private UUID userId;
                }
                """);

        assertTrue(compile());
        assertTrue(readGenerated("com/example/AddressId.java").contains("public record AddressId(UUID value)"));
    }

    @Test
    void findsIdInheritedFromAMappedSuperclass() throws IOException {
        addSource("com.example.BaseComplaint", """
                package com.example;

                import jakarta.persistence.Id;
                import java.util.UUID;

                public class BaseComplaint {
                    @Id
                    private UUID id;
                }
                """);
        addSource("com.example.UserComplaint", """
                package com.example;

                import jakarta.persistence.Entity;

                @Entity
                public class UserComplaint extends BaseComplaint {}
                """);

        assertTrue(compile());
        assertTrue(readGenerated("com/example/UserComplaintId.java")
                .contains("public record UserComplaintId(UUID value)"));
    }

    @Test
    void skipsEntitiesWhoseIdIsNotAUuid() throws IOException {
        addSource("com.example.Legacy", """
                package com.example;

                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;

                @Entity
                public class Legacy {
                    @Id
                    private Long id;
                }
                """);

        assertTrue(compile(), "a non-UUID id should be skipped, not fail the build");
        assertFalse(Files.exists(outputDir.resolve("com/example/LegacyId.java")), "no id should be generated");
    }

    @Test
    void ignoresClassesThatAreNotEntities() throws IOException {
        addSource("com.example.NotAnEntity", """
                package com.example;

                import jakarta.persistence.Id;
                import java.util.UUID;

                public class NotAnEntity {
                    @Id
                    private UUID id;
                }
                """);

        assertTrue(compile());
        assertFalse(Files.exists(outputDir.resolve("com/example/NotAnEntityId.java")));
    }

    @Test
    void generatedIdRejectsNull() throws IOException {
        addSource("com.example.Follow", """
                package com.example;

                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import java.util.UUID;

                @Entity
                public class Follow {
                    @Id
                    private UUID id;
                }
                """);

        assertTrue(compile());
        String generated = readGenerated("com/example/FollowId.java");
        assertTrue(generated.contains("value must not be null"), "compact constructor should reject null");
    }

    @Test
    void registersTheIdTypeGloballySoItAppliesEvenOnIdColumns() throws IOException {
        addSource("com.example.Follow", """
                package com.example;

                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import java.util.UUID;

                @Entity
                public class Follow {
                    @Id
                    private UUID id;
                }
                """);

        assertTrue(compile());

        // An AttributeConverter is forbidden on an @Id, so the id maps through an EnhancedUserType
        // registered globally via @TypeRegistration — that applies to @Id and ordinary attributes
        // alike, with no per-field @Type.
        String userType = readGenerated("com/example/FollowIdType.java");
        assertTrue(
                userType.contains("implements EnhancedUserType<FollowId>"),
                "the id must map through an EnhancedUserType so it works on @Id columns");

        String packageInfo = readGenerated("com/example/package-info.java");
        assertTrue(
                packageInfo.contains("@TypeRegistration(basicClass = FollowId.class, userType = FollowIdType.class)"),
                "the user type must be registered globally so it applies without a per-field @Type");
    }

    @Test
    void generatedIdSerializesAsABareUuid() throws IOException {
        addSource("com.example.Follow", """
                package com.example;

                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                import java.util.UUID;

                @Entity
                public class Follow {
                    @Id
                    private UUID id;
                }
                """);

        assertTrue(compile());

        String generated = readGenerated("com/example/FollowId.java");
        assertTrue(generated.contains("@JsonValue"), "id must serialize as the bare UUID, not an object");
        assertTrue(generated.contains("@JsonCreator"), "id must deserialize from a bare UUID");
    }

    @Test
    void generatedIdsShareTheEntityIdSupertype() {
        assertEquals(
                "com.tabariyya.entityidgen",
                EntityId.class.getPackageName(),
                "the shared interface must stay in its own constant package");
    }

    private void addSource(String qualifiedName, String content) {
        sources.add(
                new SimpleJavaFileObject(
                        URI.create("string:///" + qualifiedName.replace('.', '/') + ".java"),
                        JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return content;
                    }
                });
    }

    private boolean compile() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            Path classOutput = Files.createDirectories(outputDir.resolve("classes"));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(outputDir));
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classOutput));

            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, List.of(), null, sources);
            task.setProcessors(List.of(new EntityIdProcessor()));
            return task.call();
        }
    }

    private String readGenerated(String relativePath) throws IOException {
        Path path = outputDir.resolve(relativePath);
        assertTrue(Files.exists(path), () -> "expected generated source at " + relativePath);
        return Files.readString(path);
    }
}
