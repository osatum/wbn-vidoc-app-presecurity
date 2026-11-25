package com.osatum.wbn.application.dictionary.view;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ScientificCategoryDictionaryView {
    private final Long id;
    private final Long parentId;
    private final String parentPath;
    private final Integer level;
    private final String namePl;
    private final String nameEn;
    private final String nameRu;
}
