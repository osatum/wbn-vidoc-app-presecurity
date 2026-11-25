package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.domain.model.journal.Issn;
import com.osatum.wbn.domain.model.journal.IssnType;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

import java.util.Set;

@Mapper(componentModel = "spring")
public interface IssnViewSelectorMapper {
    @Named("pissn")
    default String pissn(Set<Issn> issns) {
        return selectIssnsByType(issns, IssnType.P);
    }

    @Named("eissn")
    default String eissn(Set<Issn> issns) {
        return selectIssnsByType(issns, IssnType.E);
    }

    default String selectIssnsByType(Set<Issn> issns, IssnType type) {
        if (issns == null) return null;
        Issn issn = issns.stream()
                .filter(t -> type.equals(t.getType()))
                .findFirst()
                .orElse(null);
        if (issn == null) return null;
        return issn.getContent();
    }
}
