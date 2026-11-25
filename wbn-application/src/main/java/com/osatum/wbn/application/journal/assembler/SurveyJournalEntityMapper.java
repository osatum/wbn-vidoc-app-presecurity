package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.domain.model.journal.CategoryType;
import com.osatum.wbn.domain.model.journal.IssnType;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.Title;
import com.osatum.wbn.domain.model.journal.TitleCode;
import com.osatum.wbn.domain.model.survey.model.Category;
import com.osatum.wbn.domain.model.survey.model.Issn;
import com.osatum.wbn.domain.model.survey.model.Institution;
import com.osatum.wbn.domain.model.survey.model.JournalInstitution;
import com.osatum.wbn.domain.model.survey.model.JournalTitle;
import com.osatum.wbn.domain.model.survey.model.Person;
import com.osatum.wbn.domain.model.survey.model.TitleType;
import org.springframework.stereotype.Component;

@Component
public class SurveyJournalEntityMapper {

    private static final Character ROLE_PUBLISHER = 'P';
    private static final Character ROLE_EDITORIAL_OFFICE = 'E';

    public Title toJournalTitle(Journal journal, JournalTitle source) {
        if (source == null || !Boolean.TRUE.equals(source.getActive())) {
            return null;
        }

        TitleCode code = toTitleCode(source.getTitleType());
        if (code == null) {
            return null;
        }

        Title title = new Title();
        title.setJournal(journal);
        title.setCode(code);
        title.setContent(source.getTitle());
        return title;
    }

    public com.osatum.wbn.domain.model.journal.Issn toJournalIssn(Journal journal, Issn source) {
        if (source == null || !Boolean.TRUE.equals(source.getActive())) {
            return null;
        }

        IssnType type = toIssnType(source.getType());
        if (type == null) {
            return null;
        }

        var issn = new com.osatum.wbn.domain.model.journal.Issn();
        issn.setJournal(journal);
        issn.setType(type);
        issn.setContent(source.getIssn());
        issn.setActive(source.getActive());
        issn.setYearFrom(source.getYearFrom());
        issn.setYearTo(source.getYearTo());
        return issn;
    }

    public com.osatum.wbn.domain.model.journal.JournalInstitution toJournalInstitution(
            Journal journal,
            JournalInstitution source
    ) {
        if (source == null || source.getInstitution() == null) {
            return null;
        }

        Institution surveyInstitution = source.getInstitution();
        var institution = new com.osatum.wbn.domain.model.journal.JournalInstitution();
        institution.setJournal(journal);
        institution.setName(surveyInstitution.getName());
        institution.setDepartment(surveyInstitution.getDepartment());
        institution.setStreet(surveyInstitution.getStreet());
        institution.setPostCode(surveyInstitution.getPostCode());
        institution.setCity(surveyInstitution.getCity());
        institution.setEmail(surveyInstitution.getEmail());
        institution.setPhone(surveyInstitution.getPhone());
        institution.setWww(surveyInstitution.getWww());
        if (surveyInstitution.getCountry() != null) {
            institution.setCountryCode(surveyInstitution.getCountry().getCode());
        }
        institution.setRoleCode(toJournalInstitutionRole(source.getInstitutionRole()));
        return institution;
    }

    com.osatum.wbn.domain.model.journal.Person toJournalPerson(Journal journal, Person source) {
        if (source == null) {
            return null;
        }

        var person = new com.osatum.wbn.domain.model.journal.Person();
        person.setJournal(journal);
        person.setFirstName(source.getFirstName());
        person.setLastName(source.getLastName());
        person.setMiddleName(source.getMiddleName());
        person.setTitle(source.getTitle());
        person.setEmail(source.getEmail());
        person.setDepartment(source.getDepartment());
        person.setPersonPosition(source.getPersonPosition());
        if (source.getCountry() != null) {
            person.setCountryCode(source.getCountry().getCode());
        }
        return person;
    }

    CategoryType toCategoryType(Category source) {
        return Boolean.TRUE.equals(source.getMain()) ? CategoryType.BASIC : CategoryType.OTHER;
    }

    private TitleCode toTitleCode(TitleType type) {
        if (TitleType.ENGLISH.equals(type)) {
            return TitleCode.ENGLISH;
        }
        if (TitleType.ORIGINAL.equals(type)) {
            return TitleCode.ORIGINAL;
        }
        if (TitleType.SHORT.equals(type)) {
            return TitleCode.SHORT;
        }
        return null;
    }

    private IssnType toIssnType(IssnType type) {
        if (type == null) {
            return null;
        }
        if ("P".equals(type.name())) {
            return IssnType.P;
        }
        if ("E".equals(type.name())) {
            return IssnType.E;
        }
        return null;
    }

    private String toJournalInstitutionRole(Character role) {
        if (ROLE_PUBLISHER.equals(role)) {
            return com.osatum.wbn.domain.model.journal.JournalInstitution.ROLE_PUBLISHER;
        }
        if (ROLE_EDITORIAL_OFFICE.equals(role)) {
            return com.osatum.wbn.domain.model.journal.JournalInstitution.ROLE_EDITORIAL_OFFICE;
        }
        return null;
    }
}
