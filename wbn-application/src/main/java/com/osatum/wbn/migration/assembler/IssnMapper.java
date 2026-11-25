package com.osatum.wbn.migration.assembler;

import com.osatum.wbn.domain.model.journal.Issn;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Mapper(componentModel = "spring")
public interface IssnMapper {

    String DATE_PATTERN = "yyyy-MM-dd";

    @Mapping(target = "createDate", source = "createDate", qualifiedByName = "stringToLocalDate")
    @Mapping(target = "removeDate", source = "removeDate", qualifiedByName = "stringToLocalDate")
    Issn toEntity(com.osatum.jml.migration.dto.journal.Issn dto);

    @Mapping(target = "createDate", source = "createDate", qualifiedByName = "localDateToString")
    @Mapping(target = "removeDate", source = "removeDate", qualifiedByName = "localDateToString")
    com.osatum.jml.migration.dto.journal.Issn toDto(Issn entity);

    @Mapping(target = "createDate", source = "createDate", qualifiedByName = "stringToLocalDate")
    @Mapping(target = "removeDate", source = "removeDate", qualifiedByName = "stringToLocalDate")
    void updateEntityFromDto(com.osatum.jml.migration.dto.journal.Issn dto, @MappingTarget Issn entity);

    @Named("stringToLocalDate")
    static LocalDate stringToLocalDate(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value, DateTimeFormatter.ofPattern(DATE_PATTERN));
    }

    @Named("localDateToString")
    static String localDateToString(LocalDate value) {
        if (value == null) return null;
        return value.format(DateTimeFormatter.ofPattern(DATE_PATTERN));
    }
}

