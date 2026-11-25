package com.osatum.wbn.domain.model.journal;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_title")
@Setter
@Getter
@NoArgsConstructor
/* Tytuł czasopisma  */
public class Title extends GeneratedIdEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id")
    private Journal journal;

    /* typ tytulu */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TitleCode code;
    /* tresc tytulu */
    @Column(length = 2000)
    private String content;
}