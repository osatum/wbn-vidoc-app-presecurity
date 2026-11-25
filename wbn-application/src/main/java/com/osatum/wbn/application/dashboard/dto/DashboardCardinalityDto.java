package com.osatum.wbn.application.dashboard.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardCardinalityDto {
    /*
    1. Instytucje razem.
2. Jednostki badawcze.
3. Użytkownicy razem.
4. Powiązania użytkownik-instytucja.
5. Aktywne sesje.
6. Sesje dzisiaj.
7. Sesje z ostatnich 7 dni.
8. Użytkownicy przypisani do wielu instytucji.
     */
    private long institutionsTotal;
    private long researchUnits;
    private long usersTotal;
    private long institutionUserMemberships;
    private long activeSessions;
    private long sessionsToday;
    private long sessionsLast7Days;
    private long usersAssignedToMultipleInstitutions;
}
