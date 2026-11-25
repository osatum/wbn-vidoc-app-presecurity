package com.osatum.wbn.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerDependencyTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    @Test
    void domainDoesNotDependOnOuterLayers() throws IOException {
        assertNoImports(
                "com/osatum/wbn/domain",
                List.of(
                        "com.osatum.wbn.application",
                        "com.osatum.wbn.infrastructure",
                        "com.osatum.wbn.web"
                )
        );
    }

    @Test
    void applicationDoesNotDependOnWebOrInfrastructureDetails() throws IOException {
        assertNoImports(
                "com/osatum/wbn/application",
                List.of(
                        "com.osatum.wbn.infrastructure",
                        "com.osatum.wbn.web",
                        "jakarta.persistence",
                        "jakarta.servlet"
                )
        );
    }

    @Test
    void webDoesNotDependOnInfrastructure() throws IOException {
        assertNoImports("com/osatum/wbn/web", List.of("com.osatum.wbn.infrastructure"));
    }

    @Test
    void searchInfrastructureDoesNotDependOnTheMutableRestRequest() throws IOException {
        assertNoImports("com/osatum/wbn/infrastructure/elasticsearch",
                List.of("com.osatum.wbn.application.journal.query.JournalListQuery"));
    }

    @Test
    void indexingListenerDoesNotDependOnSearchQueryHandlers() throws IOException {
        assertNoImports("com/osatum/wbn/infrastructure/elasticsearch/listener/JournalEventsListener.java",
                List.of("com.osatum.wbn.infrastructure.elasticsearch.repository.JournalListQueryHandlerImpl",
                        "com.osatum.wbn.application.journal.service.JournalListQueryHandler"));
    }

    @Test
    void searchAuditDoesNotDependOnTheMutableRestRequest() throws IOException {
        assertNoImports("com/osatum/wbn/application/system/service/JournalSearchAuditProperties.java",
                List.of("com.osatum.wbn.application.journal.query.JournalListQuery"));
    }

    @Test
    void searchCriteriaDoNotDependOnTransportOrValidationFrameworks() throws IOException {
        assertNoImports("com/osatum/wbn/application/journal/query/JournalSearchCriteria.java",
                List.of("com.fasterxml.jackson", "tools.jackson", "io.swagger", "jakarta.validation", "JournalListQuery"));
    }

    @Test
    void userFeatureDependsOnNotificationPortOnly() throws IOException {
        assertNoImports(
                "com/osatum/wbn/application/user",
                List.of("com.osatum.wbn.application.notification")
        );
    }

    private void assertNoImports(String packagePath, List<String> forbiddenImports) throws IOException {
        Path root = SOURCE_ROOT.resolve(packagePath);
        List<String> violations = new ArrayList<>();

        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                int lineNumber = 0;
                for (String line : Files.readAllLines(path)) {
                    lineNumber++;
                    String normalized = line.trim();
                    if (!normalized.startsWith("import ")) {
                        continue;
                    }
                    for (String forbiddenImport : forbiddenImports) {
                        if (normalized.contains(forbiddenImport)) {
                            violations.add("%s:%d -> %s".formatted(path, lineNumber, normalized));
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "Forbidden layer dependencies:\n" + String.join("\n", violations));
    }
}
