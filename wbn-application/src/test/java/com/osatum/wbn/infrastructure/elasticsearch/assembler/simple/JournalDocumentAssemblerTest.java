package com.osatum.wbn.infrastructure.elasticsearch.assembler.simple;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.Title;
import com.osatum.wbn.domain.model.journal.TitleCode;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JournalDocumentAssemblerTest {

    private final JournalDocumentAssembler assembler = new JournalDocumentAssembler();

    @Test
    void createsTitleSortContentWithoutEdgeQuotes() {
        Journal journal = new Journal();
        journal.setTitles(Set.of(
                title(TitleCode.ORIGINAL, "\"Kultura-Przemiany-Edukacja\""),
                title(TitleCode.ENGLISH, "&quot;Culture - Changes - Education&quot;"),
                title(TitleCode.SHORT, " \u201E\u0141\u00F3d\u017A\u201D "),
                title(TitleCode.ALTERNATIVE, "\u0421\u0420\u0410\u0412\u041D\u0415\u041D\u0418\u0415")
        ));

        JournalDocument document = assembler.toDto(new JournalDocumentParameters(journal, null));

        assertTitleSortContent(document, TitleCode.ORIGINAL, "\"Kultura-Przemiany-Edukacja\"", "Kultura-Przemiany-Edukacja", 0);
        assertTitleSortContent(document, TitleCode.ENGLISH, "&quot;Culture - Changes - Education&quot;", "Culture - Changes - Education", 0);
        assertTitleSortContent(document, TitleCode.SHORT, " \u201E\u0141\u00F3d\u017A\u201D ", "\u0141\u00F3d\u017A", 0);
        assertTitleSortContent(document, TitleCode.ALTERNATIVE, "\u0421\u0420\u0410\u0412\u041D\u0415\u041D\u0418\u0415", "\u0421\u0420\u0410\u0412\u041D\u0415\u041D\u0418\u0415", 1);
        assertThat(document.getTitleSortContentIcu()).isEqualTo("Kultura-Przemiany-Edukacja");
        assertThat(document.getTitleSortScriptRank()).isZero();
    }

    @Test
    void sortTitleFollowsTheDisplayedOriginalEnglishShortFallbackNotAlternativeTitles() {
        Journal journal = new Journal();
        journal.setTitles(Set.of(title(TitleCode.ENGLISH, "English title"), title(TitleCode.SHORT, "A short title"),
                title(TitleCode.ALTERNATIVE, "A different title")));
        JournalDocument document = assembler.toDto(new JournalDocumentParameters(journal, null));
        assertThat(document.getTitleSortContentIcu()).isEqualTo("English title");
        journal.setTitles(Set.of(title(TitleCode.SHORT, "Short title")));
        document = assembler.toDto(new JournalDocumentParameters(journal, null));
        assertThat(document.getTitleSortContentIcu()).isEqualTo("Short title");
        journal.setTitles(Set.of(title(TitleCode.ALTERNATIVE, "Alternative only")));
        document = assembler.toDto(new JournalDocumentParameters(journal, null));
        assertThat(document.getTitleSortContentIcu()).isNull();
        assertThat(document.getTitleSortScriptRank()).isNull();
    }

    @Test
    void preservesExistingInactiveTitleAndIssnRulesWhenCreatingSearchAndSortFields() {
        Journal journal = new Journal();
        Title inactive = title(TitleCode.ORIGINAL, "Inactive title");
        inactive.setState(com.osatum.wbn.domain.model.EntityState.INACTIVE);
        journal.setTitles(Set.of(inactive, title(TitleCode.ENGLISH, "Visible title")));
        var inactiveIssn = new com.osatum.wbn.domain.model.journal.Issn();
        inactiveIssn.setContent("0000-0000");
        inactiveIssn.setState(com.osatum.wbn.domain.model.EntityState.INACTIVE);
        journal.setIssns(Set.of(inactiveIssn));
        JournalDocument document = assembler.toDto(new JournalDocumentParameters(journal, null));
        assertThat(document.getTitles()).hasSize(1);
        assertThat(document.getIssns()).isEmpty();
        assertThat(document.getTitleSortContentIcu()).isEqualTo("Visible title");
        assertThat(journal.getTitles()).hasSize(2);
        assertThat(journal.getIssns()).hasSize(1);
    }

    private static void assertTitleSortContent(
            JournalDocument document,
            TitleCode code,
            String content,
            String sortContent,
            Integer sortScriptRank
    ) {
        assertThat(document.getTitles())
                .filteredOn(title -> code.equals(title.getCode()))
                .singleElement()
                .satisfies(title -> {
                    assertThat(title.getContent()).isEqualTo(content);
                    assertThat(title.getSortContent()).isEqualTo(sortContent);
                    assertThat(title.getSortContentIcu()).isEqualTo(sortContent);
                    assertThat(title.getSortScriptRank()).isEqualTo(sortScriptRank);
                });
    }

    private static Title title(TitleCode code, String content) {
        Title title = new Title();
        title.setCode(code);
        title.setContent(content);
        return title;
    }
}
