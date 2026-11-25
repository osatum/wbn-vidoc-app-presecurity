package com.osatum.wbn.application.journal.view;

import com.osatum.wbn.application.dictionary.view.MniswDisciplineDictionaryView;
import java.util.List;

public record MniswRatingView(Integer year, Boolean listed, Double points,
                              List<MniswDisciplineDictionaryView> disciplines, DecisionValue.State status) { }
