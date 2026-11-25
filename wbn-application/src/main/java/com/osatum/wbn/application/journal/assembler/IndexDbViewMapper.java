package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.domain.model.journal.IndexedDb;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface IndexDbViewMapper {
    default List<String> toView(Set<IndexedDb> indexedDbs) {
        if (indexedDbs == null) return null;
        return indexedDbs.stream().filter(x->x.getRepository()!=null).map(x-> x.getRepository().getName()).toList();
    }
}
