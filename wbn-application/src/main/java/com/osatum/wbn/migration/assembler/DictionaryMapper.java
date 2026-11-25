package com.osatum.wbn.migration.assembler;

import com.osatum.wbn.domain.model.dictionary.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DictionaryMapper {

    DictionaryRepository toEntity(com.osatum.jml.migration.dto.dictonary.DictionaryRepository dto);

    @Mapping(target = "namePl", source = "name")
    DictionaryAsjcCategory toEntity(com.osatum.jml.migration.dto.dictonary.DictionaryAsjcCategory dto);

    @Mapping(target = "namePl", source = "name")
    DictionaryScientificCategory toEntity(com.osatum.jml.migration.dto.dictonary.DictionaryScientificCategory dto);

    @Mapping(target = "namePl", source = "name")
    DictionaryTRCategory toEntity(com.osatum.jml.migration.dto.dictonary.DictionaryTRCategory dto);

    DictionaryCountry toEntity(com.osatum.jml.migration.dto.dictonary.DictionaryCountry dto);

    DictionaryLanguage toEntity(com.osatum.jml.migration.dto.dictonary.DictionaryLanguage dto);

}
