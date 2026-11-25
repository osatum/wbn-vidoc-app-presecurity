package com.osatum.wbn.domain.model.dictionary;

import com.osatum.wbn.domain.model.ManualIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
/* Slownik kategorii naukowych */
public class DictionaryScientificCategory  extends ManualIdEntity implements DictionaryCategory{

    /* identyfikator kategorii nadrzednej */
    private Long parentId;
    /* sciezka kategorii nadrzednej */
    private String parentPath;
    /* poziom kategorii */
    private Integer level;
    /* nazwa PL */
    @Column(length = 512)
    private String namePl;
    /* nazwa EN */
    @Column(length = 512)
    private String nameEn;
    /* nazwa RU */
    @Column(length = 512)
    private String nameRu;
}

