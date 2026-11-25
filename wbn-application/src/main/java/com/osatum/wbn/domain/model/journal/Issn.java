package com.osatum.wbn.domain.model.journal;



import com.osatum.wbn.domain.model.GeneratedIdEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "journal_issn")
@Setter
@Getter
@NoArgsConstructor
/* Klasa przechowujaca informacje o ISSN */
public class Issn extends GeneratedIdEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id")
    private Journal journal;
    /** wartosc issn */
    private String content;
    /** posiada issn od roku */
    private Integer yearFrom;
    /** posiada issn do roku */
    private Integer yearTo;
    /* flaga czy issn jest aktywny */
    private Boolean active;
    /* data utworzenia i usuniecia */
    private LocalDate createDate;
    /* data usuniecia */
    private LocalDate removeDate;
    /* Typ ISSN */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IssnType type;
    /* Flaga czy issn jes glowny */
    private Boolean main;
    /* kolejnosc */
    @Column(name = "order_no")
    private Integer order;
}
