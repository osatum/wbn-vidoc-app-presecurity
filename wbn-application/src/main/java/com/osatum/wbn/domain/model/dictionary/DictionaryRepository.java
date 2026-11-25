package com.osatum.wbn.domain.model.dictionary;

import com.osatum.wbn.domain.model.ManualIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
/* Slownik baz indeksujacych */
public class DictionaryRepository  extends ManualIdEntity implements DictionaryIdItem {
    /* nazwa */
    @Column(length = 512)
    private String name;
}
