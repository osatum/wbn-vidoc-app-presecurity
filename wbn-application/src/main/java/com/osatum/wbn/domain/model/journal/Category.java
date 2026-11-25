package com.osatum.wbn.domain.model.journal;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import com.osatum.wbn.domain.model.dictionary.DictionaryCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Setter
@Getter
@NoArgsConstructor
@MappedSuperclass
/* kategoria czasopisma */
public abstract class Category extends GeneratedIdEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id")
    private Journal journal;

    /* kod kategorii */
    @Column(length = 20)
    private String code;
    /* typ kategorii */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CategoryType categoryType;

    public abstract DictionaryCategory getCategory();
}
