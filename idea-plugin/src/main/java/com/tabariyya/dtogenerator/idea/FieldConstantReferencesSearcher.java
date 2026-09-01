package com.tabariyya.dtogenerator.idea;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public class FieldConstantReferencesSearcher
        extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

    public FieldConstantReferencesSearcher() {
        super(true);
    }

    @Override
    public void processQuery(
            ReferencesSearch.@NotNull SearchParameters parameters, @NotNull Processor<? super PsiReference> consumer) {
        for (PsiField constant : FieldsAugmentProvider.constantsOf(parameters.getElementToSearch())) {
            ReferencesSearch.search(constant, parameters.getScopeDeterminedByUser(), false).forEach(consumer);
        }
    }
}
