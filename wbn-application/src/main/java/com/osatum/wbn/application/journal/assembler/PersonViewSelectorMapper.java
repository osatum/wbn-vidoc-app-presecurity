package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.application.journal.view.PersonView;
import com.osatum.wbn.domain.model.journal.Person;
import com.osatum.wbn.domain.model.journal.PersonPosition;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = {PersonViewMapper.class})
public abstract class PersonViewSelectorMapper {

    @Autowired
    private PersonViewMapper mapper;

    @Named("editorInChief")
    List<PersonView> editorInChief(Set<Person> persons) {
        return selectPersonsByRole(persons, PersonPosition.EDITOR_IN_CHIEF);
    }

    @Named("assistanceEditor")
    List<PersonView> assistanceEditor(Set<Person> persons) {
        return selectPersonsByRole(persons, PersonPosition.ASSISTANCE_EDITOR);
    }

    @Named("deputyEditorInChief")
    List<PersonView> deputyEditorInChief(Set<Person> persons) {
        return selectPersonsByRole(persons, PersonPosition.DEPUTY_EDITOR_IN_CHIEF);
    }

    @Named("leadEditor")
    List<PersonView> leadEditor(Set<Person> persons) {
        return selectPersonsByRole(persons, PersonPosition.LEAD_EDITOR);
    }

    @Named("office")
    List<PersonView> office(Set<Person> persons) {
        return selectPersonsByRole(persons, PersonPosition.OFFICE);
    }

    @Named("editorialSecretaries")
    List<PersonView> editorialSecretaries(Set<Person> persons) {
        return selectPersonsByRole(persons, PersonPosition.EDITORIAL_SECRETARY);
    }

    List<PersonView> selectPersonsByRole(Set<Person> persons, PersonPosition type) {
        if (persons == null) return null;
        return persons.stream()
                .filter(t -> type.equals(t.getPersonPosition()))
                .map(mapper::toView)
                .toList();
    }
}
