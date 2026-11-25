package com.osatum.wbn.application.institution.query;

public record InstitutionListSort(
        InstitutionListSortField field,
        SortDirection direction
) {
    public static InstitutionListSort defaultSort() {
        return new InstitutionListSort(
                InstitutionListSortField.NAME,
                SortDirection.ASC
        );
    }
}