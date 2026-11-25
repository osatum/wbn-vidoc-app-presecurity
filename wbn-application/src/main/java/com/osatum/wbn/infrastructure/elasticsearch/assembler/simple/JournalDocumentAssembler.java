package com.osatum.wbn.infrastructure.elasticsearch.assembler.simple;

import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.journal.*;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import com.osatum.wbn.shared.assembler.AssemblerDto;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class JournalDocumentAssembler implements AssemblerDto<Journal, JournalDocument, JournalDocumentParameters> {

    private static final Pattern HTML_QUOTE_ENTITY = Pattern.compile(
            "(?i)&(?:quot|ldquo|rdquo|bdquo|laquo|raquo|#34|#x22|#8220|#8221|#8222|#x201c|#x201d|#x201e);"
    );
    private static final Pattern EDGE_SORT_CHARACTERS = Pattern.compile("^[\\p{P}\\p{S}\\p{Z}\\p{C}]+|[\\p{P}\\p{S}\\p{Z}\\p{C}]+$");

    @Override
    public JournalDocument toDto(JournalDocumentParameters parameters) {

        Journal journal = parameters.getEntity();

        JournalDocument journalDocument = parameters.getDto() == null ? new JournalDocument() : parameters.getDto();

        if (journal.getState() != null){
            journalDocument.setState(journal.getState().name());
        }

        if (journal.getWarningCode() != null){
            journalDocument.setWarningCode(journal.getWarningCode());
        } else {
            journalDocument.setWarningCode("OK");
        }

        journalDocument.setId(journal.getId());
        if (journal.getCountry() != null) {
            journalDocument.setCountryCode(journal.getCountry().getCode());
        }

        journalDocument.setDoi(journal.getDoi());

        if (journal.getCover() != null) {
            journalDocument.setCover(journal.getCover().getPath());
        }

        if (journal.getCardinality() != null) {
            journalDocument.setNumberOfPublications(journal.getCardinality().getNumberOfPublications());
            journalDocument.setPercentFullText(journal.getCardinality().getPercentFullText());
            journalDocument.setPercentAbstracts(journal.getCardinality().getPercentAbstracts());
            journalDocument.setPercentKeywords(journal.getCardinality().getPercentKeywords());
            journalDocument.setPercentReferences(journal.getCardinality().getPercentReferences());
        }

        if (journal.getTitles() != null) {
            journalDocument.setTitles(new ArrayList<>());

            for (Title source : journal.getTitles()) {
                if (isNotActive(source.getState())) {
                    continue;
                }
                String sortContent = sortContent(source.getContent());
                journalDocument.getTitles().add(new com.osatum.wbn.infrastructure.elasticsearch.document.journal.Title(
                        source.getCode(),
                        source.getContent(),
                        sortContent,
                        sortContent,
                        sortScriptRank(sortContent)
                ));
            }
        }
        applyTitleSort(journalDocument);
        if (journal.getIssns() != null) {
            journalDocument.setIssns(new ArrayList<>());
            for (Issn source : journal.getIssns()) {
                if (isNotActive(source.getState())) {
                    continue;
                }
                journalDocument.getIssns().add(new com.osatum.wbn.infrastructure.elasticsearch.document.journal.Issn(source.getType(), source.getContent()));
            }
        }
        if (journal.getInstitutions() != null) {
            journalDocument.setInstitutions(new ArrayList<>());
            for (JournalInstitution source : journal.getInstitutions()) {
                if (isNotActive(source.getState())) {
                    continue;
                }
                journalDocument.getInstitutions().add(new com.osatum.wbn.infrastructure.elasticsearch.document.journal.Institution(source.getRoleCode(), source.getName()));
            }
        }
        if (journal.getLanguages() != null) {
            journalDocument.setLanguages(new ArrayList<>());
            for (Language source : journal.getLanguages()) {
                if (isNotActive(source.getState())) {
                    continue;
                }
                if (source.getLanguage() != null) {
                    journalDocument.getLanguages().add(new com.osatum.wbn.infrastructure.elasticsearch.document.journal.Language(source.getLanguage().getCode()));
                }

            }
        }
        if (journal.getRatings() != null) {
            journalDocument.setRatings(new ArrayList<>());
            for (Rating source : journal.getRatings()) {
                if (isNotActive(source.getState())) {
                    continue;
                }

                com.osatum.wbn.infrastructure.elasticsearch.document.journal.Rating rating = new com.osatum.wbn.infrastructure.elasticsearch.document.journal.Rating();
                rating.setRatingCode(source.getRatingCode());
                rating.setYear(source.getYear());
                rating.setValueNum(source.getValueNum());
                rating.setValueStr(source.getValueStr());
                rating.setRatingStatus(source.getRatingStatus());
                if (source.getMniswDisciplines() != null && !source.getMniswDisciplines().isEmpty()) {

                    rating.setDisciplines(new ArrayList<>());

                    for (MniswDiscipline discipline : source.getMniswDisciplines()) {
                        com.osatum.wbn.infrastructure.elasticsearch.document.journal.MniswDiscipline mniswDiscipline = new com.osatum.wbn.infrastructure.elasticsearch.document.journal.MniswDiscipline();
                        mniswDiscipline.setCode(discipline.getDisciplineCode());
                        rating.getDisciplines().add(mniswDiscipline);
                    }
                }
                journalDocument.getRatings().add(rating);
            }
        }
        if (journal.getScientificCategories() != null) {
            journalDocument.setScientificCategories(new ArrayList<>());
            for (ScientificCategory source : journal.getScientificCategories()) {
                if (isNotActive(source.getState())) {
                    continue;
                }
                if (source.getCategory() != null){
                    journalDocument.getScientificCategories().add(new com.osatum.wbn.infrastructure.elasticsearch.document.journal.ScientificCategory(String.valueOf(source.getCategory().getId())));
                }

            }
        }
        return journalDocument;
    }

    private void applyTitleSort(JournalDocument document) {
        document.setTitleSortContentIcu(null);
        document.setTitleSortScriptRank(null);
        if (document.getTitles() == null) {
            return;
        }
        for (TitleCode code : new TitleCode[]{TitleCode.ORIGINAL, TitleCode.ENGLISH, TitleCode.SHORT}) {
            var title = document.getTitles().stream().filter(value -> code.equals(value.getCode())).findFirst();
            if (title.isPresent()) {
                document.setTitleSortContentIcu(title.get().getSortContentIcu());
                document.setTitleSortScriptRank(title.get().getSortScriptRank());
                return;
            }
        }
    }

    private static boolean isNotActive(EntityState source) {
        if (source == null) {
            return false;
        }
        return !EntityState.ACTIVE.equals(source);
    }

    private static String sortContent(String content) {
        if (content == null) {
            return null;
        }
        String withoutHtmlQuotes = HTML_QUOTE_ENTITY.matcher(content).replaceAll("");
        String value = EDGE_SORT_CHARACTERS.matcher(withoutHtmlQuotes).replaceAll("");
        return value.isBlank() ? null : value;
    }

    private static Integer sortScriptRank(String sortContent) {
        if (sortContent == null || sortContent.isBlank()) {
            return null;
        }
        int firstCodePoint = sortContent.codePointAt(0);
        Character.UnicodeScript script = Character.UnicodeScript.of(firstCodePoint);
        if (Character.isDigit(firstCodePoint) || Character.UnicodeScript.LATIN.equals(script)) {
            return 0;
        }
        if (Character.isLetter(firstCodePoint)) {
            return 1;
        }
        return 2;
    }
}
