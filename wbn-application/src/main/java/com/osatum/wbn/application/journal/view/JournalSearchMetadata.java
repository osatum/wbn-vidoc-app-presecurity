package com.osatum.wbn.application.journal.view;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record JournalSearchMetadata(List<Integer> mniswPointClasses, BigDecimal icvMin, BigDecimal icvMax,
                                    Map<String, Long> countryCounts, Map<String, Long> languageCounts,
                                    Map<String, Long> disciplineCounts, Map<String, Long> repositoryCounts,
                                    Map<String, Long> licenseCounts, Map<String, Long> accessModelCounts,
                                    Map<String, Long> frequencyCounts, Map<String, Long> featureCounts,
                                    Map<String, Long> mniswDisciplineCounts, Map<String, Long> reviewModelCounts) {
}
