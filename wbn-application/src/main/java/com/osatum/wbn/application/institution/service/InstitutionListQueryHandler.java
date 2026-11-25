package com.osatum.wbn.application.institution.service;

import com.osatum.wbn.application.institution.dto.InstitutionListViewDto;
import com.osatum.wbn.application.institution.query.InstitutionListQuery;
import com.osatum.wbn.application.shared.list.PageQueryHandler;

public interface InstitutionListQueryHandler extends PageQueryHandler<InstitutionListQuery, InstitutionListViewDto> {
}
