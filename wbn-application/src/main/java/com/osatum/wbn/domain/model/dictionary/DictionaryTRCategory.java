package com.osatum.wbn.domain.model.dictionary;

import com.osatum.wbn.domain.model.ManualIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
/**
 * Slownik kategorii: Kategoria Web of Science
 */
public class DictionaryTRCategory  extends ManualIdEntity implements DictionaryCategory{

    /* identyfikator kategorii nadrzednej */
    private Long parentId;
    /* sciezka kategorii nadrzednej */
    private String parentPath;
    /* poziom kategorii */
    private Integer level;

    /* Nazwa polska  */
    @Column(length = 512)
    private String namePl;
    /* Nazwa angielska   */
    @Column(length = 512)
    private String nameEn;

    /* Nazwa rosyjska   */
    @Column(length = 512)
    private String nameRu;
    /* Opis */
    @Column(length = 512)
    private String description;
}
