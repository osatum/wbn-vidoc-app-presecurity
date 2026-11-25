package com.osatum.wbn.application.questionnaire.assembler;

import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.domain.model.survey.model.Cooperator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SurveyDtoMapperTest {
    @Test
    void missingReviewerCountryMapsToNullWithoutCallingCountryDictionary() {
        SurveyDtoMapper mapper = mock(SurveyDtoMapper.class, CALLS_REAL_METHODS);
        DictionaryCountryService countries = mock(DictionaryCountryService.class);
        ReflectionTestUtils.setField(mapper, "countryService", countries);
        Cooperator reviewer = new Cooperator();
        reviewer.setFullName("Anna Nowak");

        assertThat(mapper.cooperatorToCountryView(reviewer)).isNull();
        verifyNoInteractions(countries);
    }
}
