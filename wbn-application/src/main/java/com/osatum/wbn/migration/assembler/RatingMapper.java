package com.osatum.wbn.migration.assembler;

import com.osatum.wbn.domain.model.journal.Rating;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RatingMapper {
    Rating toEntity(com.osatum.jml.migration.dto.journal.Rating dto);

    com.osatum.jml.migration.dto.journal.Rating toDto(Rating entity);
}
