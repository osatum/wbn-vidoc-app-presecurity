package com.osatum.wbn.domain.model.journal;


import com.osatum.wbn.domain.model.GeneratedIdEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_institution")
@Setter
@Getter
@NoArgsConstructor
/* Instytucja powiazana z czasopismem */
public class JournalInstitution extends GeneratedIdEntity {

    public static final String ROLE_PUBLISHER = "P";
    public static final String ROLE_EDITORIAL_OFFICE = "E";

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "journal_id")
    private Journal journal;
    /* nazwa */
    private String name;

    private String department;
    /* ulica */
    private String street;
    /* kod pocztowy */
    private String postCode;
    /* miasto */
    private String city;
    /* kod kraju */
    private String countryCode;
    /* email */
    private String email;
    /* telefon */
    private String phone;
    /* strona www */
    private String www;
    private String roleCode;
}
