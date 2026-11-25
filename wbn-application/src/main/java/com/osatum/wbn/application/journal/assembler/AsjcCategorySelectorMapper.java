package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.domain.model.journal.AsjcCategory;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class AsjcCategorySelectorMapper extends GenericCategoryViewSelectorMapper<AsjcCategory> {
}
