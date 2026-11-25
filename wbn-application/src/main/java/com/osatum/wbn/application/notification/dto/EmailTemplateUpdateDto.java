package com.osatum.wbn.application.notification.dto;

import com.osatum.wbn.domain.model.EntityState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailTemplateUpdateDto {
    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    @Size(max = 16)
    private String language;

    @NotBlank
    @Size(max = 256)
    private String subject;

    @NotBlank
    private String htmlBody;

    private String textBody;

    @NotNull
    private EntityState state;
}
