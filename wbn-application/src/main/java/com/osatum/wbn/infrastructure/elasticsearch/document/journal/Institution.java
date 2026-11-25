package com.osatum.wbn.infrastructure.elasticsearch.document.journal;

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
public class Institution {
    @Field(type = FieldType.Keyword, normalizer = "code_normalizer")
    private String roleCode;
    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "edge_ngram_analyzer", searchAnalyzer = "standard"),
            otherFields = {
                    @InnerField(suffix = "search", type = FieldType.Keyword, normalizer = "p1_text_normalizer"),
                    @InnerField(suffix = "keyword", type = FieldType.Keyword),
                    @InnerField(suffix = "autocomplete", type = FieldType.Text,
                            analyzer = "edge_ngram_analyzer", searchAnalyzer = "standard")
            }
    )
    private String name;

}
