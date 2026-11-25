import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { NavigationAuditVisitsService as NavigationAuditVisitsApi } from '../backend';
import type { NavigationAuditVisitListQuery, NavigationAuditVisitPageResult } from '../backend';

@Injectable({ providedIn: 'root' })
export class NavigationAuditVisitListService {
  private readonly api = inject(NavigationAuditVisitsApi);

  findVisits(query: NavigationAuditVisitListQuery): Observable<NavigationAuditVisitPageResult> {
    return this.api.findNavigationAuditVisits(query, 'body', false, {
      httpHeaderAccept: 'application/json',
      transferCache: false,
    });
  }
}
