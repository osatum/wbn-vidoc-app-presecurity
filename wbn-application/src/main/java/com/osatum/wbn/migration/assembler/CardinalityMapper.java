package com.osatum.wbn.migration.assembler;

import com.osatum.wbn.domain.model.journal.Cardinality;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CardinalityMapper {
    Cardinality toEntity(com.osatum.jml.migration.dto.journal.Cardinality dto);

    com.osatum.jml.migration.dto.journal.Cardinality toDto(Cardinality entity);
}
