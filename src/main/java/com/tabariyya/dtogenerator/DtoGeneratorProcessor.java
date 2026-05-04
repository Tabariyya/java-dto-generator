package com.tabariyya.dtogenerator;

import com.google.auto.service.AutoService;
import com.tabariyya.dtogenerator.annotations.Field;
import com.tabariyya.dtogenerator.annotations.GenerateDto;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.*;
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

        Set<String> actualFieldNames =
                ElementFilter.fieldsIn(sourceType.getEnclosedElements())
                        .stream()
                        .filter(field ->
                                !field.getModifiers().contains(Modifier.STATIC))
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

        for (VariableElement existingField :
                ElementFilter.fieldsIn(sourceType.getEnclosedElements())) {

            String fieldName = existingField.getSimpleName().toString();

            if (existingField.getModifiers().contains(Modifier.STATIC)
                    || fieldsToRemove.contains(fieldName)) {
                continue;
            }

            String existingFieldFqn =
                    processingEnv.getTypeUtils()
                            .erasure(existingField.asType())
                            .toString();

            List<AnnotationMirror> annotations =
                    new ArrayList<AnnotationMirror>();

            for (AnnotationMirror annotation :
                    existingField.getAnnotationMirrors()) {

                String annotationPackage =
                        packageName(annotation.getAnnotationType().toString());

                if (shouldKeepAnnotation(annotationPackage)) {
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

            fields.add(new DtoField(
                    dtoField.name(),
                    getTypeName(dtoField),
                    Collections.<AnnotationMirror>emptyList()));
        }

        writeRecord(method, newClassName, fields);
    }

    private String getTypeName(Field field) {
        try {
            return field.type().getCanonicalName();
        } catch (MirroredTypeException mte) {
            return mte.getTypeMirror().toString();
        }
    }

    private void writeRecord(Element originatingElement,
                             String className,
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

                Set<String> imports = new TreeSet<String>();

                for (DtoField field : fields) {

                    if (needsImport(packageName(field.typeFqn), packageName)) {
                        imports.add(field.typeFqn);
                    }

                    for (AnnotationMirror annotation : field.annotations) {
                        String annotationFQN =
                                annotation.getAnnotationType().toString();

                        if (needsImport(
                                packageName(annotationFQN), packageName)) {
                            imports.add(annotationFQN);
                        }
                    }
                }

                for (String imp : imports) {
                    writer.write("import " + imp + ";\n");
                }

                if (!imports.isEmpty()) {
                    writer.write("\n");
                }

                writer.write("public class " + className + " {\n\n");

                for (DtoField field : fields) {

                    for (AnnotationMirror annotation : field.annotations) {
                        writer.write("    " +
                                renderAnnotation(annotation) + "\n");
                    }

                    writer.write("    private "
                            + simpleName(field.typeFqn)
                            + " "
                            + field.name
                            + ";\n");
                }

                writer.write("\n    public " + className + "() {}\n\n");

                writer.write("    public " + className + "(\n");
                String params =
                        fields.stream()
                                .map(f -> "            "
                                        + simpleName(f.typeFqn)
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
                    String type = simpleName(field.typeFqn);
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

    private boolean shouldKeepAnnotation(String annotationPackageName) {
        return annotationPackageName.contains("constraints");
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

        DtoField(String name,
                 String typeFqn,
                 List<AnnotationMirror> annotations) {
            this.name = name;
            this.typeFqn = typeFqn;
            this.annotations = annotations;
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