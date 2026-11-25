package com.osatum.wbn.application.journal.assembler;


import com.osatum.wbn.application.journal.view.PersonView;
import com.osatum.wbn.domain.model.journal.Person;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface PersonViewMapper {

    @Mapping(target = "fullName", source = "person", qualifiedByName = "fullName")
    PersonView toView(Person person);

    @Named("fullName")
    static String buildFullName(Person person) {

        StringBuilder sb = new StringBuilder();
        if (person.getFirstName() != null){
            sb.append(person.getFirstName());
        }
        if (!sb.isEmpty()){
            sb.append(" ");
        }
        if (person.getMiddleName() != null){
            sb.append(person.getMiddleName());
        }
        if (!sb.isEmpty()){
            sb.append(" ");
        }
        sb.append(person.getLastName());
        if (!sb.isEmpty()){
            return sb.toString();
        }
        return null;
    }
}
