package com.osatum.wbn.migration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationImportErrorDto {
    private String type;
    private String fileName;
    private String message;
}
