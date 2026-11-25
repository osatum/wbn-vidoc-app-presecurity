package com.osatum.wbn.infrastructure.elasticsearch.document.journal;

import com.osatum.wbn.domain.model.journal.TitleCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Title {
    @Field(type = FieldType.Keyword, normalizer = "code_normalizer") // analizowane, full-text
    private TitleCode code;
    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "edge_ngram_analyzer", searchAnalyzer = "standard"),
            otherFields = {
                    @InnerField(suffix = "search", type = FieldType.Keyword, normalizer = "p1_text_normalizer"),
                    @InnerField(suffix = "keyword", type = FieldType.Keyword),
                    @InnerField(suffix = "sort", type = FieldType.Keyword, normalizer = "sort_normalizer"),
                    @InnerField(suffix = "autocomplete", type = FieldType.Text,
                            analyzer = "edge_ngram_analyzer", searchAnalyzer = "standard")
            }
    )
    private String content;
    @Field(type = FieldType.Keyword, normalizer = "sort_normalizer")
    private String sortContent;
    private String sortContentIcu;
    private Integer sortScriptRank;

    public Title(TitleCode code, String content) {
        this(code, content, null, null, null);
    }
}
