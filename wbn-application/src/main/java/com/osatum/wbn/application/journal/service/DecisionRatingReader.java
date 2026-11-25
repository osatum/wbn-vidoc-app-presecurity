package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.dictionary.service.DictionaryMniswDisciplineService;
import com.osatum.wbn.application.dictionary.view.MniswDisciplineDictionaryView;
import com.osatum.wbn.application.journal.view.DecisionValue.State;
import com.osatum.wbn.application.journal.view.JournalDecisionView.IcvHistory;
import com.osatum.wbn.application.journal.view.MniswRatingView;
import com.osatum.wbn.domain.model.journal.Rating;
import com.osatum.wbn.domain.entity.RatingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** Punkty i dyscypliny MNiSW pochodzą z jednej oceny MNW; rok wykazu jest niezależny od roku ankiety Karty. */
@Service
@RequiredArgsConstructor
public class DecisionRatingReader {
    private final ConfigurationService configuration;
    private final DictionaryMniswDisciplineService disciplines;

    public MniswRatingView mnisw(Collection<Rating> ratings) {
        int year = configuration.getCurrentMniswYear();
        var selected = safe(ratings).stream().filter(value -> Rating.MNW_RATING_CODE.equals(value.getRatingCode()) && Objects.equals(value.getYear(), year)).toList();
        if (selected.isEmpty()) return new MniswRatingView(year, false, null, List.of(), State.AVAILABLE);
        if (selected.size() != 1) return new MniswRatingView(year, null, null, List.of(), State.INVALID);
        var rating = selected.getFirst();
        var result = new TreeMap<String, MniswDisciplineDictionaryView>();
        boolean invalid = !validPoints(rating.getValueNum());
        if (rating.getMniswDisciplines() != null) for (var assignment : rating.getMniswDisciplines()) {
            if (assignment == null || assignment.getDisciplineCode() == null || assignment.getDisciplineCode().isBlank()) { invalid = true; continue; }
            var dictionary = disciplines.getByCode(assignment.getDisciplineCode());
            if (dictionary == null) { invalid = true; continue; }
            result.put(dictionary.getCode(), new MniswDisciplineDictionaryView(dictionary.getCode(), dictionary.getNamePl(), dictionary.getNameEn(), dictionary.getNameRu()));
        }
        return new MniswRatingView(year, true, validPoints(rating.getValueNum()) ? rating.getValueNum() : null,
                List.copyOf(result.values()), invalid ? State.INVALID : State.AVAILABLE);
    }

    /** Historia zawiera tylko rzeczywiste, jednoznaczne oceny; nie dopisuje brakujących lat jako N/E. */
    public List<IcvHistory> history(Collection<Rating> ratings) {
        var grouped = new TreeMap<Integer, List<Rating>>(Comparator.reverseOrder());
        safe(ratings).stream().filter(value -> Rating.ICV_RATING_CODE.equals(value.getRatingCode()) && value.getYear() != null && value.getYear() > 0)
                .forEach(value -> grouped.computeIfAbsent(value.getYear(), key -> new ArrayList<>()).add(value));
        return grouped.values().stream().filter(value -> value.size() == 1).map(List::getFirst)
                .filter(value -> value.getRatingStatus() == RatingStatus.INDEXED || value.getRatingStatus() == RatingStatus.NOT_INDEXED)
                .map(value -> new IcvHistory(value.getYear(), value.getRatingStatus(),
                        value.getRatingStatus() == RatingStatus.INDEXED && validPoints(value.getValueNum()) ? value.getValueNum() : null)).toList();
    }

    private List<Rating> safe(Collection<Rating> ratings) { return ratings == null ? List.of() : ratings.stream().filter(Objects::nonNull).toList(); }
    private boolean validPoints(Double value) { return value != null && Double.isFinite(value) && value >= 0; }
}
