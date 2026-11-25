package com.osatum.wbn.domain.model.journal;

import com.osatum.wbn.domain.model.dictionary.DictionaryScientificCategory;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_scientific_category")
@Setter
@Getter
@NoArgsConstructor
/* Kategoria naukowa casopisma  */
public class ScientificCategory extends Category {

    /* wzkaznik na kategorie */
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private DictionaryScientificCategory category;
}
