package com.osatum.wbn.infrastructure.elasticsearch.document.journal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Language {
    @Field(type = FieldType.Keyword, normalizer = "code_normalizer")
    private String code;
}
