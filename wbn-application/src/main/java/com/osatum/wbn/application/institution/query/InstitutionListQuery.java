package com.osatum.wbn.application.institution.query;

import com.osatum.wbn.application.shared.list.PageRequest;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstitutionListQuery extends PageRequest {

    public enum SortBy {
        SORT_BY_NAME_ASC,
        SORT_BY_NAME_DESC
    }

    private String name;
    private InstitutionType institutionType;
    private SortBy sortBy = SortBy.SORT_BY_NAME_ASC;
}
