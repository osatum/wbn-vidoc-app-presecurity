package com.osatum.wbn.domain.model.journal;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_paid_article")
@Setter
@Getter
@NoArgsConstructor
/**
 * Jezyki powiazane z czasopismem
 */
public class PaidArticle extends GeneratedIdEntity {
    private PaidArticleType paidType;
    /** Wysokość opłaty ({@code SURVEY_CREATE.ARTICLE_COST}). */
    private Integer value;
    /** Kod waluty (np. PLN/EUR). */
    private String currencyCode;
}
