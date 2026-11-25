package com.osatum.wbn.infrastructure.persistence.specifications;

import com.osatum.wbn.application.institution.query.InstitutionListQuery;
import com.osatum.wbn.application.shared.list.SortOrderDef;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstitutionSpecificationsTest {
    private final InstitutionSpecifications specifications = new InstitutionSpecifications();
    private Root<Institution> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Predicate andPredicate;

    @BeforeEach
    void setUp() {
        root = mock();
        query = mock();
        cb = mock();
        andPredicate = mock();
        when(cb.and(any(Predicate[].class))).thenReturn(andPredicate);
    }

    @Test
    void returnsNameAscendingSortByDefault() {
        InstitutionListQuery query = new InstitutionListQuery();

        assertThat(specifications.sortByFilter(query)).containsExactly(
                new SortOrderDef("name", SortOrderDef.Direction.ASC),
                new SortOrderDef("id", SortOrderDef.Direction.ASC)
        );
    }

    @Test
    void returnsNameDescendingSort() {
        InstitutionListQuery query = new InstitutionListQuery();
        query.setSortBy(InstitutionListQuery.SortBy.SORT_BY_NAME_DESC);

        assertThat(specifications.sortByFilter(query)).containsExactly(
                new SortOrderDef("name", SortOrderDef.Direction.DESC),
                new SortOrderDef("id", SortOrderDef.Direction.ASC)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtersByNameIgnoringCaseAndInstitutionType() {
        Path<String> namePath = mock(Path.class);
        Expression<String> lowerName = mock(Expression.class);
        Path<InstitutionType> institutionTypePath = mock(Path.class);
        Predicate namePredicate = mock();
        Predicate typePredicate = mock();
        when(root.<String>get("name")).thenReturn(namePath);
        when(cb.lower(namePath)).thenReturn(lowerName);
        when(cb.like(lowerName, "%alpha%")).thenReturn(namePredicate);
        when(root.<InstitutionType>get("institutionType")).thenReturn(institutionTypePath);
        when(cb.equal(institutionTypePath, InstitutionType.RESEARCH_UNIT)).thenReturn(typePredicate);

        InstitutionListQuery query = new InstitutionListQuery();
        query.setName(" Alpha ");
        query.setInstitutionType(InstitutionType.RESEARCH_UNIT);

        Predicate result = specifications.byFilter(query).toPredicate(root, this.query, cb);

        assertThat(result).isSameAs(andPredicate);
        verify(cb).like(lowerName, "%alpha%");
        verify(cb).equal(institutionTypePath, InstitutionType.RESEARCH_UNIT);
        assertPredicates(namePredicate, typePredicate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtersOnlyByInstitutionTypeWhenNameIsBlank() {
        Path<InstitutionType> institutionTypePath = mock(Path.class);
        Predicate typePredicate = mock();
        when(root.<InstitutionType>get("institutionType")).thenReturn(institutionTypePath);
        when(cb.equal(institutionTypePath, InstitutionType.SYSTEM_OPERATOR)).thenReturn(typePredicate);

        InstitutionListQuery query = new InstitutionListQuery();
        query.setName(" ");
        query.setInstitutionType(InstitutionType.SYSTEM_OPERATOR);

        Predicate result = specifications.byFilter(query).toPredicate(root, this.query, cb);

        assertThat(result).isSameAs(andPredicate);
        verify(root, never()).get("name");
        assertPredicates(typePredicate);
    }

    private void assertPredicates(Predicate... predicates) {
        ArgumentCaptor<Predicate[]> predicatesCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(predicatesCaptor.capture());
        assertThat(predicatesCaptor.getValue()).containsExactly(predicates);
    }
}
