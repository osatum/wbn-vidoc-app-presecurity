package com.osatum.wbn.application.questionnaire.assembler;

import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.application.dictionary.service.DictionaryScientificCategoryService;
import com.osatum.wbn.application.journal.assembler.CountryViewMapper;
import com.osatum.wbn.application.journal.view.CountryView;
import com.osatum.wbn.application.questionnaire.dto.SurveyDto;
import com.osatum.wbn.application.questionnaire.dto.model.AuthorsFee;
import com.osatum.wbn.application.questionnaire.dto.model.FeeDetail;
import com.osatum.wbn.application.questionnaire.dto.model.Cooperator;
import com.osatum.wbn.application.questionnaire.dto.model.Person;
import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.service.journal.AuthorFeeSelectionPolicy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class SurveyDtoMapper {

    @Autowired
    private DictionaryCountryService countryService;
    @Autowired
    private CountryViewMapper countryViewMapper;
    @Autowired
    protected AuthorFeeSelectionPolicy authorFeeSelectionPolicy;

    public abstract SurveyDto toDto(Survey person);

    @Mapping(target = "feeSelectionStatus", expression = "java(authorFeeSelectionPolicy.resolve(fee).status())")
    @Mapping(target = "selectedFeeDetail", expression = "java(toDto(authorFeeSelectionPolicy.resolve(fee).selectedFeeDetail()))")
    public abstract AuthorsFee toDto(com.osatum.wbn.domain.model.survey.model.AuthorsFee fee);

    public abstract FeeDetail toDto(com.osatum.wbn.domain.model.survey.model.FeeDetail detail);

    @Mapping(target = "country", expression = "java(personToCountryView(person))")
    public abstract Person toDto(com.osatum.wbn.domain.model.journal.Person person);

    @Mapping(target = "country", expression = "java(cooperatorToCountryView(person))")
    public abstract Cooperator toDto(com.osatum.wbn.domain.model.survey.model.Cooperator person);

    public CountryView cooperatorToCountryView(com.osatum.wbn.domain.model.survey.model.Cooperator cooperator) {
        if (cooperator.getCountry() == null) {
            return null;
        }
        DictionaryCountry country = countryService.getByCode(cooperator.getCountry());
        if (country == null){
            return null;
        }
        return countryViewMapper.toView(country);
    }


    public CountryView personToCountryView(com.osatum.wbn.domain.model.journal.Person person) {
        DictionaryCountry country = countryService.getByCode(person.getCountryCode());
        if (country == null){
            return null;
        }
        return countryViewMapper.toView(country);
    }
}
