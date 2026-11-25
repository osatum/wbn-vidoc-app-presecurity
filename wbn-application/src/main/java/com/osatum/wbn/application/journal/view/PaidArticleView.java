package com.osatum.wbn.application.journal.view;

import com.osatum.wbn.domain.model.journal.PaidArticleType;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaidArticleView {
    private PaidArticleType paidType;
    private Integer value;
    private String currencyCode;
}
