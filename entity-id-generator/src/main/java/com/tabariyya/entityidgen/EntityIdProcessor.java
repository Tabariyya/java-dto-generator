package com.tabariyya.entityidgen;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

/**
 * Generates a strongly typed identifier record for every {@code @Entity} class.
 *
 * <p>For an entity {@code com.example.Follow} whose {@code @Id} field is a {@link java.util.UUID},
 * this emits {@code com.example.FollowId} — a record wrapping that UUID and implementing {@link
 * EntityId}.
 *
 * <p>The JPA annotations are resolved by name rather than by class literal, so this module needs no
 * compile-time dependency on {@code jakarta.persistence}.
 */
@SupportedAnnotationTypes(EntityIdProcessor.ENTITY_ANNOTATION)
public final class EntityIdProcessor extends AbstractProcessor {

    static final String ENTITY_ANNOTATION = "jakarta.persistence.Entity";
    private static final String ID_ANNOTATION = "jakarta.persistence.Id";
    private static final String UUID_TYPE = "java.util.UUID";
    private static final String OBJECT_TYPE = "java.lang.Object";
    private static final String ID_SUFFIX = "Id";
    private static final String TYPE_SUFFIX = "Type";

    /** Guards against re-creating a source file when an entity is seen in more than one round. */
    private final Set<String> generated = new HashSet<>();

    /** Ids generated per package, used to emit that package's type registrations. */
    private final Map<String, SortedSet<String>> idsByPackage = new TreeMap<>();

    /** Packages whose {@code package-info} has already been written. */
    private final Set<String> registeredPackages = new HashSet<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        TypeElement entityAnnotation = processingEnv.getElementUtils().getTypeElement(ENTITY_ANNOTATION);
        if (entityAnnotation == null) {
            return false;
        }

        for (TypeElement entity : ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(entityAnnotation))) {
            generateFor(entity);
        }

        // Written at the end of the round rather than in the final one: the Filer refuses to create
        // sources in the last round, and nothing here generates further entities to wait for.
        writePackageRegistrations();
        return false;
    }

    private void generateFor(TypeElement entity) {
        Set<VariableElement> idFields = idFieldsOf(entity);

        if (idFields.isEmpty()) {
            // Composite keys (@EmbeddedId / @IdClass) and inherited ids have no single UUID to wrap.
            note(entity, "no @Id field found; skipping strongly typed id generation");
            return;
        }
        if (idFields.size() > 1) {
            note(entity, "composite @Id (" + idFields.size() + " fields); skipping strongly typed id generation");
            return;
        }

        VariableElement idField = idFields.iterator().next();
        String idType = processingEnv.getTypeUtils().erasure(idField.asType()).toString();
        String ownIdName = entity.getSimpleName() + ID_SUFFIX;

        // An entity keyed by its own generated id must keep generating it, or adopting the type would
        // delete the very class the field refers to. During the -proc:only pass that type is not
        // resolvable yet and appears under its simple name, so accept either form.
        boolean keyedByOwnId = idType.equals(ownIdName) || idType.endsWith("." + ownIdName);

        if (!UUID_TYPE.equals(idType) && !keyedByOwnId) {
            note(
                    entity,
                    "@Id field '" + idField.getSimpleName() + "' is " + idType + ", not " + UUID_TYPE + " or "
                            + ownIdName + "; skipping strongly typed id generation");
            return;
        }

        write(entity);
    }

    /**
     * Collects the {@code @Id} fields for an entity, walking up the superclass chain so that ids
     * inherited from a {@code @MappedSuperclass} are found. The most derived class that declares an
     * id wins, matching how JPA resolves the primary key.
     */
    private Set<VariableElement> idFieldsOf(TypeElement entity) {
        for (TypeElement current = entity; current != null; current = superclassOf(current)) {
            Set<VariableElement> idFields = new LinkedHashSet<>();
            for (VariableElement field : ElementFilter.fieldsIn(current.getEnclosedElements())) {
                if (hasAnnotation(field, ID_ANNOTATION)) {
                    idFields.add(field);
                }
            }
            if (!idFields.isEmpty()) {
                return idFields;
            }
        }
        return Set.of();
    }

    private static TypeElement superclassOf(TypeElement type) {
        TypeMirror superclass = type.getSuperclass();
        if (superclass.getKind() != TypeKind.DECLARED) {
            return null;
        }
        Element element = ((DeclaredType) superclass).asElement();
        if (!(element instanceof TypeElement superType)
                || superType.getQualifiedName().contentEquals(OBJECT_TYPE)) {
            return null;
        }
        return superType;
    }

    private static boolean hasAnnotation(Element element, String qualifiedName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            Element annotationType = mirror.getAnnotationType().asElement();
            if (annotationType.getKind() == ElementKind.ANNOTATION_TYPE
                    && ((TypeElement) annotationType).getQualifiedName().contentEquals(qualifiedName)) {
                return true;
            }
        }
        return false;
    }

    private void write(TypeElement entity) {
        String packageName = processingEnv
                .getElementUtils()
                .getPackageOf(entity)
                .getQualifiedName()
                .toString();
        String idName = entity.getSimpleName() + ID_SUFFIX;
        String qualifiedName = packageName.isEmpty() ? idName : packageName + "." + idName;

        if (!generated.add(qualifiedName)) {
            return;
        }

        writeIdRecord(entity, packageName, idName, qualifiedName);
        writeUserType(entity, packageName, idName, qualifiedName + TYPE_SUFFIX);
        idsByPackage.computeIfAbsent(packageName, key -> new TreeSet<>()).add(idName);
    }

    /**
     * Emits a {@code package-info} registering every generated {@code UserType} for the package.
     *
     * <p>A global registration is what keeps entity code annotation-free: Hibernate then knows the id
     * types wherever they appear, including on an {@code @Id}, where JPA forbids an
     * {@code AttributeConverter} and a {@code @Type} would otherwise be needed on each field.
     */
    private void writePackageRegistrations() {
        for (Map.Entry<String, SortedSet<String>> entry : idsByPackage.entrySet()) {
            String packageName = entry.getKey();
            if (packageName.isEmpty() || !registeredPackages.add(packageName)) {
                continue;
            }

            Filer filer = processingEnv.getFiler();
            String qualifiedName = packageName + ".package-info";
            try (PrintWriter out =
                    new PrintWriter(filer.createSourceFile(qualifiedName).openWriter())) {
                out.println("@TypeRegistrations({");
                SortedSet<String> idNames = entry.getValue();
                int remaining = idNames.size();
                for (String idName : idNames) {
                    out.println("    @TypeRegistration(basicClass = " + idName + ".class, userType = " + idName
                            + TYPE_SUFFIX + ".class)" + (--remaining > 0 ? "," : ""));
                }
                out.println("})");
                out.println("package " + packageName + ";");
                out.println();
                out.println("import org.hibernate.annotations.TypeRegistration;");
                out.println("import org.hibernate.annotations.TypeRegistrations;");
            } catch (IOException e) {
                processingEnv
                        .getMessager()
                        .printMessage(
                                Diagnostic.Kind.ERROR, "Failed to generate " + qualifiedName + ": " + e.getMessage());
            }
        }
    }

    private void writeIdRecord(TypeElement entity, String packageName, String idName, String qualifiedName) {
        Filer filer = processingEnv.getFiler();
        try (PrintWriter out =
                new PrintWriter(filer.createSourceFile(qualifiedName, entity).openWriter())) {
            if (!packageName.isEmpty()) {
                out.println("package " + packageName + ";");
                out.println();
            }
            out.println("import com.fasterxml.jackson.annotation.JsonCreator;");
            out.println("import com.fasterxml.jackson.annotation.JsonValue;");
            out.println("import " + EntityId.class.getCanonicalName() + ";");
            out.println();
            out.println("import java.util.UUID;");
            out.println();
            out.println("/** Strongly typed identifier for {@link " + entity.getSimpleName() + "}. */");
            out.println("public record " + idName + "(UUID value) implements EntityId {");
            out.println();
            out.println("    public " + idName + " {");
            out.println("        if (value == null) {");
            out.println("            throw new IllegalArgumentException(\"" + idName + " value must not be null\");");
            out.println("        }");
            out.println("    }");
            out.println();
            out.println("    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)");
            out.println("    public static " + idName + " of(UUID value) {");
            out.println("        return new " + idName + "(value);");
            out.println("    }");
            out.println();
            out.println("    /** A new id wrapping a random UUID. */");
            out.println("    public static " + idName + " random() {");
            out.println("        return new " + idName + "(UUID.randomUUID());");
            out.println("    }");
            out.println();
            out.println("    /** Serializes as the bare UUID, so wrapping an id never changes a JSON contract. */");
            out.println("    @JsonValue");
            out.println("    @Override");
            out.println("    public UUID value() {");
            out.println("        return value;");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public String toString() {");
            out.println("        return value.toString();");
            out.println("    }");
            out.println("}");
        } catch (IOException e) {
            processingEnv
                    .getMessager()
                    .printMessage(
                            Diagnostic.Kind.ERROR,
                            "Failed to generate " + qualifiedName + ": " + e.getMessage(),
                            entity);
        }
    }

    private void writeUserType(TypeElement entity, String packageName, String idName, String qualifiedName) {
        String typeName = idName + TYPE_SUFFIX;

        Filer filer = processingEnv.getFiler();
        try (PrintWriter out =
                new PrintWriter(filer.createSourceFile(qualifiedName, entity).openWriter())) {
            if (!packageName.isEmpty()) {
                out.println("package " + packageName + ";");
                out.println();
            }
            out.println("import org.hibernate.engine.spi.SharedSessionContractImplementor;");
            out.println("import org.hibernate.type.SqlTypes;");
            out.println("import org.hibernate.usertype.EnhancedUserType;");
            out.println();
            out.println("import java.io.Serializable;");
            out.println("import java.sql.PreparedStatement;");
            out.println("import java.sql.ResultSet;");
            out.println("import java.sql.SQLException;");
            out.println("import java.sql.Types;");
            out.println("import java.util.Objects;");
            out.println("import java.util.UUID;");
            out.println();
            out.println("/** Maps {@link " + idName + "} to a UUID column, including as an {@code @Id}. */");
            out.println("public final class " + typeName + " implements EnhancedUserType<" + idName + "> {");
            out.println();
            out.println("    @Override");
            out.println("    public int getSqlType() {");
            out.println("        return SqlTypes.UUID;");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public Class<" + idName + "> returnedClass() {");
            out.println("        return " + idName + ".class;");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public boolean equals(" + idName + " x, " + idName + " y) {");
            out.println("        return Objects.equals(x, y);");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public int hashCode(" + idName + " x) {");
            out.println("        return Objects.hashCode(x);");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public " + idName + " nullSafeGet(");
            out.println(
                    "            ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)");
            out.println("            throws SQLException {");
            out.println("        UUID value = rs.getObject(position, UUID.class);");
            out.println("        return value == null ? null : new " + idName + "(value);");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public void nullSafeSet(");
            out.println("            PreparedStatement st, " + idName
                    + " value, int index, SharedSessionContractImplementor session)");
            out.println("            throws SQLException {");
            out.println("        if (value == null) {");
            out.println("            st.setNull(index, Types.OTHER);");
            out.println("        } else {");
            out.println("            st.setObject(index, value.value());");
            out.println("        }");
            out.println("    }");
            out.println();
            out.println("    /** Records are immutable, so the instance can be shared. */");
            out.println("    @Override");
            out.println("    public " + idName + " deepCopy(" + idName + " value) {");
            out.println("        return value;");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public boolean isMutable() {");
            out.println("        return false;");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public Serializable disassemble(" + idName + " value) {");
            out.println("        return value == null ? null : value.value();");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public " + idName + " assemble(Serializable cached, Object owner) {");
            out.println("        return cached == null ? null : new " + idName + "((UUID) cached);");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public String toSqlLiteral(" + idName + " value) {");
            out.println("        return value == null ? null : \"'\" + value.value() + \"'\";");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public String toString(" + idName + " value) {");
            out.println("        return value == null ? null : value.value().toString();");
            out.println("    }");
            out.println();
            out.println("    @Override");
            out.println("    public " + idName + " fromStringValue(CharSequence sequence) {");
            out.println("        return sequence == null ? null : new " + idName
                    + "(UUID.fromString(sequence.toString()));");
            out.println("    }");
            out.println("}");
        } catch (IOException e) {
            processingEnv
                    .getMessager()
                    .printMessage(
                            Diagnostic.Kind.ERROR,
                            "Failed to generate " + qualifiedName + ": " + e.getMessage(),
                            entity);
        }
    }

    private void note(TypeElement entity, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, entity.getQualifiedName() + ": " + message);
    }
}
