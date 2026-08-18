package com.tabariyya.dtogenerator;

import com.google.auto.service.AutoService;
import com.tabariyya.dtogenerator.annotations.AddAnnotation;
import com.tabariyya.dtogenerator.annotations.Field;
import com.tabariyya.dtogenerator.annotations.GenerateDto;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

@AutoService(Processor.class)
public class DtoGeneratorProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new HashSet<String>();
        types.add(GenerateDto.class.getCanonicalName());
        return types;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        log(NOTE, "Processor started");

        Set<? extends Element> annotatedElements =
                roundEnv.getElementsAnnotatedWith(GenerateDto.class);

        processElements(annotatedElements);

        log(NOTE, "Processor finished");
        return false;
    }

    private void processElements(Set<? extends Element> annotatedElements) {
        for (Element element : annotatedElements) {
            log(NOTE, "Processing: " + element.getSimpleName());

            if (element instanceof ExecutableElement) {
                processMethod((ExecutableElement) element);
            }

            log(NOTE, "Processed: " + element.getSimpleName());
        }
    }

    private void processMethod(ExecutableElement method) {
        GenerateDto generateDto = method.getAnnotation(GenerateDto.class);

        String methodName = method.getSimpleName().toString();
        String newClassName =
                Character.toUpperCase(methodName.charAt(0)) +
                        methodName.substring(1);

        String sourceClassFqn = method.getReturnType().toString();

        Set<String> fieldsToRemove =
                new HashSet<String>(Arrays.asList(generateDto.removeFields()));

        TypeElement sourceType =
                processingEnv.getElementUtils().getTypeElement(sourceClassFqn);

        if (sourceType == null) {
            log(ERROR, "Could not find source class: " + sourceClassFqn);
            return;
        }

        List<VariableElement> sourceFields = collectInstanceFields(sourceType);

        Set<String> actualFieldNames =
                sourceFields
                        .stream()
                        .map(field -> field.getSimpleName().toString())
                        .collect(Collectors.toSet());

        for (String fieldToRemove : fieldsToRemove) {
            if (!actualFieldNames.contains(fieldToRemove)) {
                log(ERROR,
                        "Field '" + fieldToRemove +
                                "' specified in removeFields does not exist in source class "
                                + sourceClassFqn);
            }
        }

        List<DtoField> fields = new ArrayList<DtoField>();

        for (VariableElement existingField : sourceFields) {

            String fieldName = existingField.getSimpleName().toString();

            if (fieldsToRemove.contains(fieldName)) {
                continue;
            }

            String existingFieldFqn =
                    cleanTypeSignature(fieldTypeAsMemberOf(sourceType, existingField).toString());

            List<AnnotationMirror> annotations =
                    new ArrayList<AnnotationMirror>();

            for (AnnotationMirror annotation :
                    existingField.getAnnotationMirrors()) {

                if (shouldKeepAnnotation(annotation)) {
                    annotations.add(annotation);
                }
            }

            fields.add(new DtoField(fieldName, existingFieldFqn, annotations));
        }

        for (Field dtoField : generateDto.addFields()) {

            boolean duplicate = false;
            for (DtoField f : fields) {
                if (f.name.equals(dtoField.name())) {
                    duplicate = true;
                    break;
                }
            }

            if (duplicate) {
                log(ERROR,
                        "Field with name '" + dtoField.name()
                                + "' already exists in " + newClassName);
                continue;
            }

            List<String> rawAnnotations = new ArrayList<String>();
            for (AddAnnotation addAnnotation : dtoField.annotations()) {
                String fqn = getAddAnnotationTypeName(addAnnotation);
                rawAnnotations.add(buildRawAnnotation(fqn, addAnnotation.params()));
            }

            fields.add(new DtoField(
                    dtoField.name(),
                    getTypeName(dtoField),
                    Collections.<AnnotationMirror>emptyList(),
                    rawAnnotations));
        }

        writeRecord(method, newClassName, getExtendsTypeName(generateDto), fields);
    }

    /**
     * Resolves a field's type as seen through the source class, substituting the type arguments the source
     * class supplies to its superclasses. A field declared as {@code Box<T>} on {@code Base<T>} therefore
     * reports {@code Box<String>} for {@code Sub extends Base<String>}, rather than leaking the type
     * variable {@code T} into a generated class that does not declare it. Fields declared on the source
     * class itself resolve to the type they are declared with.
     */
    private TypeMirror fieldTypeAsMemberOf(TypeElement sourceType, VariableElement field) {
        try {
            return processingEnv
                    .getTypeUtils()
                    .asMemberOf((DeclaredType) sourceType.asType(), field);
        } catch (IllegalArgumentException e) {
            return field.asType();
        }
    }

    /**
     * Collects the non-static fields of the source class and every superclass up to (excluding) java.lang.Object,
     * ordered superclass-first. A subclass field shadowing a superclass field wins, keeping the superclass position.
     */
    private List<VariableElement> collectInstanceFields(TypeElement sourceType) {
        Deque<TypeElement> hierarchy = new ArrayDeque<TypeElement>();

        for (TypeElement current = sourceType; current != null; ) {
            hierarchy.addFirst(current);

            TypeMirror superclass = current.getSuperclass();
            if (superclass.getKind() != TypeKind.DECLARED) {
                break;
            }

            TypeElement superElement =
                    (TypeElement) ((DeclaredType) superclass).asElement();
            if (superElement.getQualifiedName().contentEquals("java.lang.Object")) {
                break;
            }

            current = superElement;
        }

        Map<String, VariableElement> fields = new LinkedHashMap<String, VariableElement>();
        for (TypeElement type : hierarchy) {
            for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                if (!field.getModifiers().contains(Modifier.STATIC)) {
                    fields.put(field.getSimpleName().toString(), field);
                }
            }
        }

        return new ArrayList<VariableElement>(fields.values());
    }

    private String getExtendsTypeName(GenerateDto generateDto) {
        try {
            return generateDto.extend().getCanonicalName();
        } catch (MirroredTypeException mte) {
            return mte.getTypeMirror().toString();
        }
    }

    private String getTypeName(Field field) {
        try {
            return field.type().getCanonicalName();
        } catch (MirroredTypeException mte) {
            return mte.getTypeMirror().toString();
        }
    }

    private String getAddAnnotationTypeName(AddAnnotation annotation) {
        try {
            return annotation.value().getCanonicalName();
        } catch (MirroredTypeException mte) {
            return mte.getTypeMirror().toString();
        }
    }

    private String buildRawAnnotation(String fqn, String params) {
        if (params.isEmpty()) {
            return "@" + fqn;
        }
        return "@" + fqn + "(" + params + ")";
    }

    private void writeRecord(Element originatingElement,
                             String className,
                             String extendsFqn,
                             List<DtoField> fields) {

        try {
            String packageName =
                    processingEnv.getElementUtils()
                            .getPackageOf(originatingElement)
                            .getQualifiedName()
                            .toString();

            String qualifiedClassName = packageName + "." + className;

            JavaFileObject file =
                    processingEnv.getFiler()
                            .createSourceFile(qualifiedClassName);

            Writer writer = file.openWriter();
            try {
                writer.write("package " + packageName + ";\n\n");

                boolean hasSuperclass = !extendsFqn.equals("java.lang.Object");

                Set<String> imports = new TreeSet<String>();

                if (hasSuperclass && needsImport(packageName(extendsFqn), packageName)) {
                    imports.add(extendsFqn);
                }

                for (DtoField field : fields) {

                    for (String fqn : extractFqns(field.typeFqn)) {
                        if (needsImport(packageName(fqn), packageName)) {
                            imports.add(fqn);
                        }
                    }

                    for (AnnotationMirror annotation : field.annotations) {
                        String annotationFQN =
                                annotation.getAnnotationType().toString();

                        if (needsImport(
                                packageName(annotationFQN), packageName)) {
                            imports.add(annotationFQN);
                        }
                    }

                    for (String raw : field.rawAnnotations) {
                        String fqn = extractRawAnnotationFqn(raw);
                        if (needsImport(packageName(fqn), packageName)) {
                            imports.add(fqn);
                        }
                    }
                }

                for (String imp : imports) {
                    writer.write("import " + imp + ";\n");
                }

                if (!imports.isEmpty()) {
                    writer.write("\n");
                }

                writer.write("public class " + className
                        + (hasSuperclass ? " extends " + simpleName(extendsFqn) : "")
                        + " {\n\n");

                for (DtoField field : fields) {

                    for (AnnotationMirror annotation : field.annotations) {
                        writer.write("    " +
                                renderAnnotation(annotation) + "\n");
                    }

                    for (String raw : field.rawAnnotations) {
                        writer.write("    " +
                                renderRawAnnotation(raw) + "\n");
                    }

                    writer.write("    private "
                            + simplifyType(field.typeFqn)
                            + " "
                            + field.name
                            + ";\n");
                }

                writer.write("\n    public " + className + "() {}\n\n");

                writer.write("    public " + className + "(\n");
                String params =
                        fields.stream()
                                .map(f -> "            "
                                        + simplifyType(f.typeFqn)
                                        + " "
                                        + f.name)
                                .collect(Collectors.joining(",\n"));
                writer.write(params + "\n    ) {\n");
                for (DtoField field : fields) {
                    writer.write("        this." + field.name
                            + " = " + field.name + ";\n");
                }
                writer.write("    }\n\n");

                for (DtoField field : fields) {
                    String type = simplifyType(field.typeFqn);
                    String name = field.name;

                    String capitalised =
                            Character.toUpperCase(name.charAt(0))
                                    + name.substring(1);

                    writer.write("    public " + type + " get" + capitalised
                            + "() { return " + name + "; }\n");
                    writer.write("    public void set" + capitalised
                            + "(" + type + " " + name + ") { this."
                            + name + " = " + name + "; }\n\n");
                }

                writer.write("}\n");

            } finally {
                writer.close();
            }

        } catch (Exception e) {
            log(ERROR, "Failed to write DTO: " + e.getMessage());
        }
    }

    private String renderAnnotation(AnnotationMirror annotation) {
        StringBuilder sb = new StringBuilder("@");
        sb.append(annotation.getAnnotationType()
                .asElement()
                .getSimpleName());

        Map<? extends ExecutableElement,
                ? extends AnnotationValue> values =
                annotation.getElementValues();

        if (!values.isEmpty()) {
            sb.append("(");

            String params =
                    values.entrySet()
                            .stream()
                            .map(entry ->
                                    entry.getKey().getSimpleName()
                                            + " = "
                                            + entry.getValue())
                            .collect(Collectors.joining(", "));

            sb.append(params).append(")");
        }

        return sb.toString();
    }

    // Extracts the FQN from a raw annotation string like "@com.example.Foo(value = 1)"
    private String extractRawAnnotationFqn(String raw) {
        int start = raw.startsWith("@") ? 1 : 0;
        int end = raw.indexOf('(');
        if (end == -1) end = raw.length();
        return raw.substring(start, end).trim();
    }

    // Replaces FQN with simple name: "@com.example.Foo(x)" → "@Foo(x)"
    private String renderRawAnnotation(String raw) {
        String fqn = extractRawAnnotationFqn(raw);
        String simple = simpleName(fqn);
        return raw.replace(fqn, simple);
    }

    private boolean shouldKeepAnnotation(AnnotationMirror annotation) {
        String annotationPackageName =
                packageName(annotation.getAnnotationType().toString());

        if (annotationPackageName.contains("constraints")) {
            return true;
        }

        // Also keep custom annotations that are meta-annotated with @Constraint
        // (the standard marker for custom Jakarta Bean Validation annotations)
        TypeElement annotationType =
                (TypeElement) annotation.getAnnotationType().asElement();
        for (AnnotationMirror meta : annotationType.getAnnotationMirrors()) {
            String metaFqn = meta.getAnnotationType().toString();
            if (metaFqn.equals("jakarta.validation.Constraint")
                    || metaFqn.equals("javax.validation.Constraint")) {
                return true;
            }
        }

        return false;
    }

    private static final Pattern FQN_PATTERN =
            Pattern.compile("[a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)+");

    private String cleanTypeSignature(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("@[a-zA-Z_$][a-zA-Z0-9_$.]*(?:\\((?:[^)(]+|\\([^)(]*\\))*\\))?", "");
        cleaned = cleaned.replaceAll("\\s*\\.\\s*", ".");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private List<String> extractFqns(String typeSignature) {
        String cleaned = cleanTypeSignature(typeSignature);
        List<String> fqns = new ArrayList<String>();
        Matcher matcher = FQN_PATTERN.matcher(cleaned);
        while (matcher.find()) {
            fqns.add(matcher.group());
        }
        return fqns;
    }

    private String simplifyType(String typeSignature) {
        String cleaned = cleanTypeSignature(typeSignature);
        Matcher matcher = FQN_PATTERN.matcher(cleaned);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String fqn = matcher.group();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(simpleName(fqn)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String simpleName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot == -1 ? fqn : fqn.substring(lastDot + 1);
    }

    String packageName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot == -1 ? "" : fqn.substring(0, lastDot);
    }

    boolean needsImport(String packageName, String currentPackage) {
        return !packageName.isEmpty()
                && !packageName.equals("java.lang")
                && !packageName.equals(currentPackage);
    }

    private static class DtoField {
        final String name;
        final String typeFqn;
        final List<AnnotationMirror> annotations;
        final List<String> rawAnnotations;

        DtoField(String name,
                 String typeFqn,
                 List<AnnotationMirror> annotations) {
            this(name, typeFqn, annotations, Collections.<String>emptyList());
        }

        DtoField(String name,
                 String typeFqn,
                 List<AnnotationMirror> annotations,
                 List<String> rawAnnotations) {
            this.name = name;
            this.typeFqn = typeFqn;
            this.annotations = annotations;
            this.rawAnnotations = rawAnnotations;
        }
    }

    private void log(Diagnostic.Kind level, String message) {
        processingEnv.getMessager().printMessage(
                level,
                String.format(
                        "%s [%s]: %s",
                        DtoGeneratorProcessor.class.getSimpleName(),
                        level.name(),
                        message));
    }
}