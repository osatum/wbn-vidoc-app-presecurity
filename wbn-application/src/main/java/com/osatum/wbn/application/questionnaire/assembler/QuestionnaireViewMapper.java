package com.osatum.wbn.application.questionnaire.assembler;

import com.osatum.wbn.application.questionnaire.view.QuestionnaireView;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {SurveyDtoMapper.class})
public interface QuestionnaireViewMapper {
    @Mapping(source = "surveyData.survey", target = "survey")
    @Mapping(target = "detailedSectionEnabled", ignore = true)
    QuestionnaireView toView(Questionnaire questionnaire);
}
