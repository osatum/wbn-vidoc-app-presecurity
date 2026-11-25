package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.application.journal.view.CardinalityView;
import com.osatum.wbn.domain.model.journal.Cardinality;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CardinalityViewMapper {
    CardinalityView toView(Cardinality entity);
}
