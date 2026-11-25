package com.osatum.wbn.application.institution.dto;

import com.osatum.wbn.domain.model.institution.IpAddressFamily;
import com.osatum.wbn.domain.model.institution.IpRuleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstitutionIpRuleCreateDto {
    @NotNull
    private IpRuleType ruleType;

    private IpAddressFamily addressFamily;

    @NotBlank
    @Size(max = 255)
    private String ruleRaw;

    @PositiveOrZero
    private int priority;
}
