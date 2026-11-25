package com.osatum.wbn.application.journal.assembler;


import com.osatum.wbn.domain.model.journal.Title;
import com.osatum.wbn.domain.model.journal.TitleCode;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

import java.util.Set;

@Mapper(componentModel = "spring")
public interface TitleViewSelectorMapper {

    @Named("english")
    default String englishTitle(Set<Title> titles) {
        return selectTitleByType(titles, TitleCode.ENGLISH);
    }

    @Named("original")
    default String originalTitle(Set<Title> titles) {
        return selectTitleByType(titles, TitleCode.ORIGINAL);
    }

    @Named("short")
    default String shortTitle(Set<Title> titles) {
        return selectTitleByType(titles, TitleCode.SHORT);
    }

    default String selectTitleByType(Set<Title> titles, TitleCode code) {
        if (titles == null) return null;
        Title title =  titles.stream()
                .filter(t -> code.equals(t.getCode()))
                .findFirst()
                .orElse(null);
        if (title == null) return null;
        return title.getContent();
    }
}
