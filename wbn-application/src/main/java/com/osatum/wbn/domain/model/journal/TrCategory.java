package com.osatum.wbn.domain.model.journal;

import com.osatum.wbn.domain.model.dictionary.DictionaryTRCategory;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_tr_category")
@Setter
@Getter
@NoArgsConstructor
/* Kategorie Kategoria Web of Science czasopisma */
public class TrCategory extends Category {
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private DictionaryTRCategory category;

}
