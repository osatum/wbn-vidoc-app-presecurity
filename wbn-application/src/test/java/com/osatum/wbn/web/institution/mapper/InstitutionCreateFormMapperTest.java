package com.osatum.wbn.web.institution.mapper;

import com.osatum.wbn.application.institution.dto.InstitutionCreateDto;
import com.osatum.wbn.domain.model.institution.AuthMode;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.web.institution.form.InstitutionCreateForm;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstitutionCreateFormMapperTest {

    private final InstitutionCreateFormMapper mapper = new InstitutionCreateFormMapper();

    @Test
    void mapsRegistrationFlags() {
        InstitutionCreateForm form = new InstitutionCreateForm();
        form.setName("Test institution");
        form.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        form.setAuthMode(AuthMode.AUTH_REQUIRED);
        form.setAllowRegistration(true);
        form.setAllowOtherIp(true);
        form.setAllowOtherEmailDomain(true);
        form.setAutoActivateNewAccounts(true);
        form.setOperatingProfile(com.osatum.wbn.domain.model.institution.OperatingProfile.BASIC);

        InstitutionCreateDto dto = mapper.toDto(form);

        assertThat(dto.isAllowRegistration()).isTrue();
        assertThat(dto.isAllowOtherIp()).isTrue();
        assertThat(dto.isAllowOtherEmailDomain()).isTrue();
        assertThat(dto.isAutoActivateNewAccounts()).isTrue();
        assertThat(dto.getOperatingProfile()).isEqualTo(com.osatum.wbn.domain.model.institution.OperatingProfile.BASIC);
    }
}
