package com.osatum.wbn.application.journal.assembler;


import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.application.journal.view.CountryView;
import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class CountryViewMapper {

    @Autowired
    DictionaryCountryService dictionaryCountryService;

    public CountryView toView(String code){
        if (code == null || code.isEmpty()) {
        }

        DictionaryCountry country = dictionaryCountryService.getByCode(code);
        if (country == null) {
            return null;
        }
        return toView(country);
    }

    public abstract CountryView toView(DictionaryCountry country);
}
