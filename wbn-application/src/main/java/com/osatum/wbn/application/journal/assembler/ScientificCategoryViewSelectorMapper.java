package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.domain.model.journal.ScientificCategory;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class ScientificCategoryViewSelectorMapper extends GenericCategoryViewSelectorMapper<ScientificCategory> {
}
