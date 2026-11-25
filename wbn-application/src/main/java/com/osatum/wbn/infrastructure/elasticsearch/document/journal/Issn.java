package com.osatum.wbn.infrastructure.elasticsearch.document.journal;


import com.osatum.wbn.domain.model.journal.IssnType;
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
public class Issn {
    @Field(type = FieldType.Keyword, normalizer = "code_normalizer")
    private IssnType type;
    @MultiField(
            mainField = @Field(type = FieldType.Keyword, normalizer = "code_normalizer"),
            otherFields = {
                    @InnerField(suffix = "search", type = FieldType.Keyword, normalizer = "p1_issn_normalizer"),
                    @InnerField(suffix = "normalized", type = FieldType.Text, analyzer = "issn_pattern")
            }
    )
    private String content;
}
