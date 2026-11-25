package com.osatum.wbn.domain.repository.institution;

import java.util.List;

public interface InstitutionDeletionRepository {

    List<InstitutionDeletionUser> findUsersWithoutOtherInstitution(Long institutionId);

    void deleteDirectDataForInstitution(Long institutionId);

    void deleteAllDataForUser(InstitutionDeletionUser user);

    void deleteMembershipsForInstitution(Long institutionId);

    void deleteInstitution(Long institutionId);
}
