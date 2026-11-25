package com.osatum.wbn.web.configuration.controller;

import com.osatum.wbn.application.configuration.dto.OperatingContextDto;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.configuration.service.OperatingProfileService;
import com.osatum.wbn.application.institution.authorization.CanInstitutionManage;
import com.osatum.wbn.domain.model.institution.OperatingProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/configuration/operating-profile")
@RequiredArgsConstructor
@Tag(name = "Configuration")
public class OperatingProfileController {
    private final OperatingProfileService operatingProfileService;
    private final ConfigurationService configurationService;

    @GetMapping
    @Operation(summary = "Efektywny profil funkcjonalny aktualnej jednostki", operationId = "getOperatingContext")
    public OperatingContextDto currentContext() {
        return operatingProfileService.currentContext();
    }

    @GetMapping("/default")
    @CanInstitutionManage
    @Operation(summary = "Domyślny profil funkcjonalny systemu", operationId = "getDefaultOperatingProfile")
    public OperatingProfile defaultProfile() {
        return configurationService.getDefaultOperatingProfile();
    }
}
