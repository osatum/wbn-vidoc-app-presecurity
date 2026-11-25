package com.osatum.wbn.application.journal.view;

import java.util.Map;

/** Liczniki opcji z uwzględnieniem pozostałych filtrów, bez zmiany słowników i zakresów walidacji. */
public record JournalSearchCounts(Map<String, Long> countryCounts, Map<String, Long> languageCounts,
                                  Map<String, Long> disciplineCounts, Map<String, Long> repositoryCounts,
                                  Map<String, Long> licenseCounts, Map<String, Long> accessModelCounts,
                                  Map<String, Long> frequencyCounts, Map<String, Long> featureCounts,
                                  Map<String, Long> mniswDisciplineCounts, Map<String, Long> reviewModelCounts) {
}
