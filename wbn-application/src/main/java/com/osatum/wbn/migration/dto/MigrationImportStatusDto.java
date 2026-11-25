package com.osatum.wbn.migration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationImportStatusDto {
    private MigrationImportState state;
    private MigrationImportStage stage;
    private int dictionaryTotal;
    private int dictionaryProcessed;
    private int dictionarySucceeded;
    private int dictionaryFailed;
    private int journalTotal;
    private int journalProcessed;
    private int journalSucceeded;
    private int journalFailed;
    private int stageTotal;
    private int stageProcessed;
    private String currentFile;
    private String message;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant updatedAt;
    @Builder.Default
    private List<MigrationImportErrorDto> errors = List.of();
}
