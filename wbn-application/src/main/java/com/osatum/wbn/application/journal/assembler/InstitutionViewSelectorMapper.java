package com.osatum.wbn.application.journal.assembler;


import com.osatum.wbn.application.journal.view.InstitutionView;
import com.osatum.wbn.domain.model.journal.JournalInstitution;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = InstitutionViewMapper.class)
public abstract class InstitutionViewSelectorMapper {

    @Autowired
    private InstitutionViewMapper mapper;

    @Named("publisher")
    String pubisher(Set<JournalInstitution> institutions) {
        List<InstitutionView> views = selectInstitutionByRole(institutions, JournalInstitution.ROLE_PUBLISHER);
        if (views.isEmpty()) {
            return null;
        }
        return views.get(0).getName();
    }

    @Named("publishers")
    List<InstitutionView> pubishers(Set<JournalInstitution> institutions) {
        return selectInstitutionByRole(institutions, JournalInstitution.ROLE_PUBLISHER);
    }

    @Named("editors")
    List<InstitutionView> editors(Set<JournalInstitution> institutions) {
        return selectInstitutionByRole(institutions, JournalInstitution.ROLE_EDITORIAL_OFFICE);
    }

    private List<InstitutionView> selectInstitutionByRole(Set<JournalInstitution> institutions, String role) {
        if (institutions == null) return null;
        return institutions.stream()
                .filter(t -> role.equals(t.getRoleCode()))
                .map(mapper::toView)
                .toList();
    }
}
