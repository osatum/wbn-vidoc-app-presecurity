package com.osatum.wbn.application.journal.assembler;


import com.osatum.wbn.application.journal.view.CountryView;
import com.osatum.wbn.application.journal.view.InstitutionView;
import com.osatum.wbn.domain.model.journal.JournalInstitution;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class InstitutionViewMapper {
    @Autowired
    private CountryViewMapper countryViewMapper;

    @Mapping(target = "country", expression = "java(resolveCountry(institution.getCountryCode()))")
    public abstract InstitutionView toView(JournalInstitution institution);


    CountryView resolveCountry(String countryCode) {
        if (countryCode == null) {
            return null;
        }
        return countryViewMapper.toView(countryCode);
    }
}
