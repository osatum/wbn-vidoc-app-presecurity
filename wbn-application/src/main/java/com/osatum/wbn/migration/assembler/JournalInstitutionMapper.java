package com.osatum.wbn.migration.assembler;

import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.journal.JournalInstitution;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface JournalInstitutionMapper {


    @Mapping(target = "state", source = "state", qualifiedByName = "stringToEntityState")
    JournalInstitution toEntity(com.osatum.jml.migration.dto.journal.Institution dto);

    @Mapping(target = "state", source = "state", qualifiedByName = "entityStateToString")
    com.osatum.jml.migration.dto.journal.Institution toDto(JournalInstitution entity);

    @Named("stringToEntityState")
    static EntityState stringToEntityState(String value) {
        if (value == null || value.isBlank()) return EntityState.ACTIVE;
        switch (value) {
            case "a":
                return EntityState.ACTIVE;
            case "u":
                return EntityState.UNVERIFIED;
            case "d":
                return EntityState.DELETED;
            case "v":
                return EntityState.VERIFIED;
            default:
                return EntityState.ACTIVE;
        }
    }

    @Named("entityStateToString")
    static String entityStateToString(EntityState value) {
        if (value == null) return null;
        switch (value) {
            case ACTIVE:
                return "a";
            case UNVERIFIED:
                return "u";
            case DELETED:
                return "d";
            case VERIFIED:
            default:
                return null;
        }
    }


}
