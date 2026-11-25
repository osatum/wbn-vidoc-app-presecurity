package com.osatum.wbn.migration.assembler;

import com.osatum.wbn.domain.model.journal.Person;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PersonMapper {
    Person toEntity(com.osatum.jml.migration.dto.journal.Person dto);

    com.osatum.jml.migration.dto.journal.Person toDto(Person entity);
}
