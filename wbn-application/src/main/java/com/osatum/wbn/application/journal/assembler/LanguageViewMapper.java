package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.application.journal.view.LanguageView;
import com.osatum.wbn.domain.model.dictionary.DictionaryLanguage;
import com.osatum.wbn.domain.model.journal.Language;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface LanguageViewMapper {

    LanguageView toView(DictionaryLanguage language);

    default List<LanguageView> toView(Set<Language> languages) {
        if (languages == null) return null;

        return languages.stream().map(Language::getLanguage).filter(Objects::nonNull).map(x -> {
            LanguageView languageView = new LanguageView();
            languageView.setNameEn(x.getNameEn());
            languageView.setNamePl(x.getNamePl());
            languageView.setNameRu(x.getNameRu());
            languageView.setCode(x.getCode());
            return languageView;
        }).toList();
    }
}
