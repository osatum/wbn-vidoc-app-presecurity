package com.osatum.wbn.domain.model.journal;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_description")
@Setter
@Getter
@NoArgsConstructor
/* Opis czasopisma */
public class Description extends GeneratedIdEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id")
    private Journal journal;
    @Enumerated(EnumType.STRING)
    /* wersja jezykowa opisu */
    @Column(length = 20)
    private DescriptionType type;
    /* opis */
    @Column(length = 10000)
    private String content;
}
