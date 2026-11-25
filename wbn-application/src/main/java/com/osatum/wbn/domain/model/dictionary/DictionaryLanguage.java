package com.osatum.wbn.domain.model.dictionary;

import com.osatum.wbn.domain.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Getter
@Setter
/**
 * Slownik jezykow
 */
public class DictionaryLanguage extends BaseEntity implements Serializable, DictionaryCodeItem {
    private static final long serialVersionUID = 1L;
    /* kod jeyka */
    @Id
    @Column(length = 2)
    private String code;
    /* nazwa en */
    @Column(length = 64)
    private String nameEn;
    /* nazwa pl */
    @Column(length = 64)
    private String namePl;
    /* nazwa ru */
    @Column(length = 64)
    private String nameRu;
    /* czy jezyk jest jezykiem kongresowym */
    private Boolean congress;

}
