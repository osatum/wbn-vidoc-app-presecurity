package com.osatum.wbn.application.institution.service;

import com.osatum.wbn.application.institution.dto.InstitutionCreateDto;
import com.osatum.wbn.application.institution.mapper.InstitutionMapper;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.institution.OperatingProfile;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.web.institution.form.InstitutionCreateForm;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class InstitutionOperatingProfileTest {
    @Test
    void mapsCreateUpdateAndExplicitInheritanceWithoutChangingOtherFields() {
        var mapper = Mappers.getMapper(InstitutionMapper.class);
        var dto = new InstitutionCreateDto();
        dto.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        dto.setName("Unit");
        dto.setOperatingProfile(OperatingProfile.BASIC);
        var institution = mapper.toEntity(dto);
        assertThat(institution.getOperatingProfile()).isEqualTo(OperatingProfile.BASIC);
        assertThat(mapper.toViewDto(institution).getOperatingProfile()).isEqualTo(OperatingProfile.BASIC);
        dto.setOperatingProfile(OperatingProfile.ADVANCED);
        mapper.updateEntity(dto, institution);
        assertThat(institution.getOperatingProfile()).isEqualTo(OperatingProfile.ADVANCED);
        var oldClient = new InstitutionCreateDto();
        oldClient.setName("Unit");
        oldClient.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        mapper.updateEntity(oldClient, institution);
        assertThat(institution.getOperatingProfile()).isEqualTo(OperatingProfile.ADVANCED);
        dto.setOperatingProfile(null);
        mapper.updateEntity(dto, institution);
        assertThat(institution.getOperatingProfile()).isNull();
        assertThat(institution.getName()).isEqualTo("Unit");
    }

    @Test
    void validatesResearchUnitOnlyInBothRestAndApplicationPaths() {
        var dto = new InstitutionCreateDto();
        dto.setInstitutionType(InstitutionType.SYSTEM_OPERATOR);
        dto.setOperatingProfile(OperatingProfile.BOTH);
        var validator = new InstitutionUseCaseValidator(mock(InstitutionRepository.class));
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateCreate(dto));
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateUpdate(1L, dto));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var form = new InstitutionCreateForm();
            form.setInstitutionType(InstitutionType.SYSTEM_OPERATOR);
            form.setOperatingProfile(OperatingProfile.BOTH);
            assertThat(factory.getValidator().validate(form)).anyMatch(violation -> violation.getPropertyPath().toString().equals("operatingProfileValid"));
            form.setOperatingProfile(null);
            assertThat(factory.getValidator().validate(form)).noneMatch(violation -> violation.getPropertyPath().toString().equals("operatingProfileValid"));
        }
    }

    @Test
    void distinguishesAnOmittedPropertyFromAnExplicitNullInJson() {
        var json = new tools.jackson.databind.ObjectMapper();
        var mapper = new com.osatum.wbn.web.institution.mapper.InstitutionCreateFormMapper();
        var omitted = mapper.toDto(json.readValue("{}", InstitutionCreateForm.class));
        assertThat(omitted.isOperatingProfileSpecified()).isFalse();
        var inherit = mapper.toDto(json.readValue("{\"operatingProfile\":null}", InstitutionCreateForm.class));
        assertThat(inherit.isOperatingProfileSpecified()).isTrue();
        assertThat(inherit.getOperatingProfile()).isNull();
    }
}
