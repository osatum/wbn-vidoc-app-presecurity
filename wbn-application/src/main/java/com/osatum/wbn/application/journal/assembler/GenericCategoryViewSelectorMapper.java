package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.application.journal.view.CategoryView;
import com.osatum.wbn.domain.model.dictionary.DictionaryCategory;
import com.osatum.wbn.domain.model.journal.Category;
import com.osatum.wbn.domain.model.journal.CategoryType;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;


public abstract class GenericCategoryViewSelectorMapper<E extends Category> {



    @Named("basic")
    public CategoryView basicCategory(Set<E> scientificCategories) {
        return selectBasicCategory(scientificCategories);
    }

    @Named("other")
    public  List<CategoryView> otherCategories(Set<E> scientificCategories) {
        return selectOtherCategories(scientificCategories);
    }

    public  List<CategoryView> selectOtherCategories(Set<E> scientificCategories) {
        if (scientificCategories == null) {
            return null;
        }
        return scientificCategories.stream().filter(x -> CategoryType.OTHER.equals(x.getCategoryType())).map(x -> mapCategory(x.getCategory())).toList();
    }

    public  CategoryView selectBasicCategory(Set<E> scientificCategories) {
        if (scientificCategories == null) {
            return null;
        }
        Category category = scientificCategories.stream().filter(x -> CategoryType.BASIC.equals(x.getCategoryType())).findFirst().orElse(null);
        if (category == null || category.getCategory() == null) {
            return null;
        }
        return mapCategory(category.getCategory());
    }

    protected CategoryView mapCategory(DictionaryCategory category){
        CategoryView result = new CategoryView();
        result.setNameEn(category.getNameEn() );
        result.setNamePl(category.getNamePl() == null ? category.getNameEn() : category.getNamePl());
        result.setNameRu(category.getNameRu() == null ? category.getNameEn() : category.getNameRu());
        return result;
    }
}
