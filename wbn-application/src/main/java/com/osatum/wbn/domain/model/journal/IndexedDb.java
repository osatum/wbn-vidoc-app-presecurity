package com.osatum.wbn.domain.model.journal;


import com.osatum.wbn.domain.model.GeneratedIdEntity;
import com.osatum.wbn.domain.model.dictionary.DictionaryRepository;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_indexed_db")
@Setter
@Getter
@NoArgsConstructor
/* Klasa przechowujaca informacje o indeksowanych bazach danych */
public class IndexedDb  extends GeneratedIdEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id")
    private Journal journal;

    /* slownik do indeksowanych baz danych */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private DictionaryRepository repository;
    private Integer yearFrom;
}
