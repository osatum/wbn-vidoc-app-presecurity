package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.domain.model.journal.TrCategory;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class TrCategoryViewSelectorMapper extends GenericCategoryViewSelectorMapper<TrCategory> {
}
