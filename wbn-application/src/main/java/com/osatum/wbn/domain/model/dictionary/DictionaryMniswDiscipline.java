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
public class DictionaryMniswDiscipline extends BaseEntity implements Serializable, DictionaryCodeItem {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(length = 20, nullable = false)
    private String code;

    @Column(length = 512, nullable = false)
    private String namePl;

    @Column(length = 512)
    private String nameEn;

    @Column(length = 512)
    private String nameRu;
}
