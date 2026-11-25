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
 * Slownik kategorii Scimago
 */
public class DictionaryAsjcCategory extends ManualIdEntity implements  DictionaryCategory{
    /* Nazwa polska */
    @Column(length = 512)
    private String namePl;
    /* Nazwa angielska */
    @Column(length = 512)
    private String nameEn;
    /* Nazwa rosyjska */
    @Column(length = 512)
    private String nameRu;
    @Column(length = 512)
    private String field;
    @Column(length = 512)
    private String subject;
}
