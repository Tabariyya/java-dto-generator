package com.tabariyya.dtogenerator;

import java.util.Collections;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;

class DtoField {

    final String name;
    final String typeFqn;
    final List<AnnotationMirror> annotations;
    final List<String> rawAnnotations;

    DtoField(String name, String typeFqn, List<AnnotationMirror> annotations) {
        this(name, typeFqn, annotations, Collections.emptyList());
    }

    DtoField(
            String name,
            String typeFqn,
            List<AnnotationMirror> annotations,
            List<String> rawAnnotations) {
        this.name = name;
        this.typeFqn = typeFqn;
        this.annotations = annotations;
        this.rawAnnotations = rawAnnotations;
    }
}
