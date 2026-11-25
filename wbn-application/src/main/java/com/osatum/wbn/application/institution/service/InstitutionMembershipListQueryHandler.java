package com.osatum.wbn.application.institution.service;

import com.osatum.wbn.application.institution.dto.InstitutionMembershipListDto;
import com.osatum.wbn.application.institution.query.InstitutionMembershipListQuery;
import com.osatum.wbn.application.shared.list.PageQueryHandler;

public interface InstitutionMembershipListQueryHandler extends PageQueryHandler<InstitutionMembershipListQuery, InstitutionMembershipListDto> {
}
