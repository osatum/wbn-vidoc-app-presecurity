package com.osatum.wbn.infrastructure.elasticsearch.document.journal;

import com.osatum.wbn.domain.entity.RatingStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Rating {
    @Field(type = FieldType.Keyword)
    private String valueStr;
    @Field(type = FieldType.Double)
    private Double valueNum;
    @Field(type = FieldType.Keyword, normalizer = "code_normalizer")
    private String ratingCode;
    @Field(type = FieldType.Keyword, normalizer = "code_normalizer")
    private RatingStatus ratingStatus;
    @Field(type = FieldType.Integer)
    private Integer year;
    @Field(type = FieldType.Nested)
    private List<MniswDiscipline> disciplines;

}
