package com.osatum.wbn.domain.model.journal;


import com.osatum.wbn.domain.model.GeneratedIdEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_cardinalities")
@Setter
@Getter
@NoArgsConstructor
/* Licznosci czasopisma */
public class Cardinality extends GeneratedIdEntity {
    /*
     * Liczba publikacji w czasopismu
     */
    private Integer numberOfPublications;
    /* Procent publikacji w czasopismie z pełnym tekstem      */
    private Integer percentFullText;
    /* Procent publikacji w czasopismie z abstraktem     */
    private Integer percentAbstracts;
    /* Procent publikacji w czasopismie z kluczowymi słowami     */
    private Integer percentKeywords;
    /* Procent publikacji w czasopismie z referencjami     */
    private Integer percentReferences;
}
