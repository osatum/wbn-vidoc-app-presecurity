package com.osatum.wbn.application.journal.assembler;


import com.osatum.wbn.application.journal.view.DescriptionView;
import com.osatum.wbn.domain.model.journal.Description;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DescriptionViewMapper {
    DescriptionView toView(Description description);
}
