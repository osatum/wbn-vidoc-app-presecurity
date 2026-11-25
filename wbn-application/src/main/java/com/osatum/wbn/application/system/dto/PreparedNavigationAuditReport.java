package com.osatum.wbn.application.system.dto;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public record PreparedNavigationAuditReport(Path path, String filename, String mediaType) implements AutoCloseable {
    public static final String XLS_MEDIA_TYPE = "application/vnd.ms-excel";
    public static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public PreparedNavigationAuditReport(Path path, String filename) {
        this(path, filename, XLSX_MEDIA_TYPE);
    }

    public InputStream openStream() throws IOException {
        return new FilterInputStream(Files.newInputStream(path)) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    PreparedNavigationAuditReport.this.close();
                }
            }
        };
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(path);
    }
}
