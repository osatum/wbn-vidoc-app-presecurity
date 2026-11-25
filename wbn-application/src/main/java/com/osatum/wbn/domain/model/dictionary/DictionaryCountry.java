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
/* slownik krajów*/
public class DictionaryCountry extends BaseEntity implements Serializable, DictionaryCodeItem {
    private static final long serialVersionUID = 1L;

    /* kod kraju*/
    @Id
    @Column(length = 2)
    private String code;
    /* kod 3 znakowy */
    @Column(length = 3)
    private String codeAlpha3;
    /* nazwa pl */
    @Column(length = 64)
    private String namePl;
    /* nazwa en */
    @Column(length = 64)
    private String nameEn;
    /* nazwa ru */
    @Column(length = 64)
    private String nameRu;
}
