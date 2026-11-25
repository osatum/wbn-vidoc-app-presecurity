package com.osatum.wbn.application.report.survey.sevice;

import com.osatum.wbn.application.report.survey.dto.QuestionnaireReportAvailabilityDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
public class QuestionnaireReportFileService {

    @Value("${app.outpux-questionaris-xls}")
    private String outputQuestionaries2024xls;

    @Value("${app.outpux-questionaris-xls-en}")
    private String outputQuestionaries2024xlsEn;

    public QuestionnaireReportAvailabilityDto availability() {
        return new QuestionnaireReportAvailabilityDto(
                isAvailable(QuestionnaireReportLanguage.PL),
                isAvailable(QuestionnaireReportLanguage.EN)
        );
    }

    public Optional<QuestionnaireReportFile> findReport(QuestionnaireReportLanguage language) {
        Optional<Path> reportPath = reportPath(language);
        if (reportPath.isEmpty() || !isReadableReport(reportPath.get())) {
            return Optional.empty();
        }

        Path path = reportPath.get();
        try {
            return Optional.of(new QuestionnaireReportFile(
                    new FileSystemResource(path),
                    Files.size(path),
                    language.fileName()
            ));
        } catch (IOException e) {
            log.warn("Cannot read questionnaire report file {}", path, e);
            return Optional.empty();
        }
    }

    private boolean isAvailable(QuestionnaireReportLanguage language) {
        return reportPath(language).filter(this::isReadableReport).isPresent();
    }

    private boolean isReadableReport(Path path) {
        return Files.isRegularFile(path) && Files.isReadable(path);
    }

    private Optional<Path> reportPath(QuestionnaireReportLanguage language) {
        String configuredPath = switch (language) {
            case PL -> outputQuestionaries2024xls;
            case EN -> outputQuestionaries2024xlsEn;
        };
        if (configuredPath == null || configuredPath.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Path.of(configuredPath).toAbsolutePath().normalize());
        } catch (InvalidPathException e) {
            log.warn("Invalid questionnaire report path configured for language {}", language.code());
            return Optional.empty();
        }
    }

    public record QuestionnaireReportFile(Resource resource, long contentLength, String fileName) {
    }

    public enum QuestionnaireReportLanguage {
        PL("pl", "questionnaire-report-2024-pl.xlsx"),
        EN("en", "questionnaire-report-2024-en.xlsx");

        private final String code;
        private final String fileName;

        QuestionnaireReportLanguage(String code, String fileName) {
            this.code = code;
            this.fileName = fileName;
        }

        public String code() {
            return code;
        }

        public String fileName() {
            return fileName;
        }

        public static Optional<QuestionnaireReportLanguage> fromCode(String code) {
            if (code == null) {
                return Optional.empty();
            }

            return switch (code.toLowerCase(Locale.ROOT)) {
                case "pl" -> Optional.of(PL);
                case "en" -> Optional.of(EN);
                default -> Optional.empty();
            };
        }
    }
}
