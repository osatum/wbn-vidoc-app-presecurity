package com.osatum.wbn.domain.model.journal;


import com.osatum.wbn.domain.model.GeneratedIdEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_person")
@Setter
@Getter
@NoArgsConstructor
/* Osoba powiazanz z czasopismem */
public class Person  extends GeneratedIdEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id")
    private Journal journal;

    /* tytul */
    @Column(length = 255)
    private String title;

    /* imie */
    @Column(length = 255)
    private String firstName;

    /* drugie imie */
    @Column(length = 255)
    private String middleName;

    /* nazwisko */
    @Column(length = 255)
    private String lastName;

    /* kod kraju */
    private String countryCode;

    /* email */
    @Column(length = 255)
    private String email;
    @Column(length = 512)
    private String department;
    private String statusCode;
    private String recordSource;

    /* pozycja osoby w czasopismie */
    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private PersonPosition personPosition;
}
