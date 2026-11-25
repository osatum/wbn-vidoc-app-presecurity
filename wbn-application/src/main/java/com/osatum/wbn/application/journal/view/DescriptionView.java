package com.osatum.wbn.application.journal.view;

import com.osatum.wbn.domain.model.journal.DescriptionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DescriptionView {
    private DescriptionType type;
    private String content;
}
