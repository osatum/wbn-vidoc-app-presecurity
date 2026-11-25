package com.osatum.wbn.web.institution.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "Potwierdzenie trwałego usunięcia instytucji")
public class InstitutionDeletionConfirmationForm {

    @NotNull
    @Schema(description = "Identyfikator jednorazowego potwierdzenia.", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID confirmationId;

    @NotBlank
    @Pattern(regexp = "[A-Z0-9]{8}")
    @Schema(description = "Ośmioznakowy kod wyświetlony operatorowi.", example = "AB34CD56", requiredMode = Schema.RequiredMode.REQUIRED)
    private String confirmationCode;
}
