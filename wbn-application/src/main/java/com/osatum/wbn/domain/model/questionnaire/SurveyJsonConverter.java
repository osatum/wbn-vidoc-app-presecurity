package com.osatum.wbn.domain.model.questionnaire;

import com.osatum.wbn.domain.model.survey.Survey;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.ObjectMapper;

@Converter(autoApply = false)
public class SurveyJsonConverter implements AttributeConverter<Survey, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Survey survey) {
        try {
            return objectMapper.writeValueAsString(survey);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize Survey", e);
        }
    }

    @Override
    public Survey convertToEntityAttribute(String json) {
        try {
            return objectMapper.readValue(json, Survey.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Survey JSON", e);
        }
    }
}
