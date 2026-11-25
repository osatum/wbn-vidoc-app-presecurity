package com.osatum.wbn.domain.model.journal;


import com.osatum.wbn.domain.model.ManualIdEntity;
import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "journal")
@Getter
@Setter
@NoArgsConstructor
/* encja opisujaca czasopismo */
public class Journal extends ManualIdEntity {

    /* typ czasopisma */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private JournalKind kind;

    /* kraj czasopisma */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_code")
    private DictionaryCountry country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affiliation_country_code")
    private DictionaryCountry affiliationCountry;

    @Column(length = 2048)
    private String affiliationName;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "paid_article_id", referencedColumnName = "id")
    private PaidArticle paidArticle;

    /* DOI czasopisma */
    @Column(length = 128)
    private String doi;

    /* licencja czasopisma */
    @Column(length = 20)
    private String oaLicenceCode;

    /* strona internetowa czasopisma */
    @Column(length = 256)
    private String website;

    /* flaga czy czasopismo ma wersje drukowana */
    private Boolean printedVersion;

    /* flaga czy czasopismo ma wersje elektroniczna */
    private Boolean electronicVersion;

    /* rok pierwszego wydania czasopisma */
    private Integer firstIssueYear;

    /* czestotliwosc wydawania czasopisma */
    @Column(length = 1)
    @Enumerated(EnumType.STRING)
    private JournalFrequency frequency;

    /* kod ostrzezenia */
    @Column(length = 20)
    private String warningCode;

    private Integer circulation;

    /* dane pliku okladki */
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "file_id")
    private FileData cover;

    /* licznosci czasopisma */
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "cardinality_id")
    private Cardinality cardinality;

    /* opisy czasopisma */
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Description> descriptions;

    /* tytuły czsoisma */
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Title> titles;

    /* issny czasopisam */
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Issn> issns;

    /* Instytucje powiazane z czasopismem  */
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<JournalInstitution> institutions;

    /* Języki powiązane z czasopismem  */
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Language> languages;

    /* Kategorie naukowe powiązane z czasopismem */
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ScientificCategory> scientificCategories;

    /* kategorie Web of Science powiazane z czasopismem */
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<TrCategory> trCategories;

    /* kategorie Scimago powiazane z czasopismem */
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<AsjcCategory> ajscCategories;

    /** bazy indeksacyje powiazane z czasopismem */
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<IndexedDb> indexedDbs;

    /* osoby powiazane z czasopismem */
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Person> persons;

    /* oceny czasopism */
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Rating> ratings;


    /* ankiety powiazane z czasopismem */
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Questionnaire> questionnaires;

}
