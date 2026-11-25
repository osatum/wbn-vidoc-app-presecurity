package com.osatum.wbn.application.journal.view;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CardinalityView {
    private Integer numberOfPublications;
    private Integer percentFullText;
    private Integer percentAbstracts;
    private Integer percentKeywords;
    private Integer percentReferences;
}
