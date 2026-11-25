package com.osatum.wbn.domain.model.journal;


import com.osatum.wbn.domain.model.GeneratedIdEntity;
import com.osatum.wbn.domain.model.dictionary.DictionaryLanguage;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_language")
@Setter
@Getter
@NoArgsConstructor
/**
 * Jezyki powiazane z czasopismem
 */
public class Language extends GeneratedIdEntity {
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "journal_id")
    private Journal journal;

    /* slownik jezyka */
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "code")
    private DictionaryLanguage language;

    /* kolejnosc */
    private Integer orderNo;

}
