import { Injectable, inject } from '@angular/core';
import { map, Observable } from 'rxjs';
import { NavigationAuditService as NavigationAuditApiService } from '../backend';
import type {
  NavigationAuditEventListQuery as OpenApiNavigationAuditEventListQuery,
  PageResult,
} from '../backend';

export type NavigationAuditEventType = OpenApiNavigationAuditEventListQuery.EventTypeEnum;

export type NavigationAuditActorType = 'USER' | 'INSTITUTION' | 'ANONYMOUS';

export type NavigationAuditEventListQuery = OpenApiNavigationAuditEventListQuery;

export interface NavigationAuditEventListDto {
  id?: number;
  eventId?: string;
  eventType?: NavigationAuditEventType;
  occurredAt?: string;
  receivedAt?: string;
  actorType?: NavigationAuditActorType;
  userId?: number;
  userEmail?: string;
  userFirstName?: string;
  userLastName?: string;
  institutionId?: number;
  institutionName?: string;
  institutionType?: string;
  institutionRole?: string;
  loginSessionId?: number;
  viewId?: string;
  pageKey?: string;
  routeTemplate?: string;
  routePath?: string;
  resourceType?: string;
  resourceId?: string;
  action?: string;
  target?: string;
  durationMs?: number;
  success?: boolean;
  statusCode?: number;
  ipAddress?: string;
  userAgent?: string;
  propertiesJson?: string;
}

export type NavigationAuditEventPageResult = Omit<PageResult, 'data'> & {
  data?: NavigationAuditEventListDto[];
};

@Injectable({
  providedIn: 'root',
})
export class NavigationAuditEventsService {
  private readonly navigationAuditApi = inject(NavigationAuditApiService);

  findEvents(query: NavigationAuditEventListQuery): Observable<NavigationAuditEventPageResult> {
    return this.navigationAuditApi.findEvents(query, 'body', false, {
      httpHeaderAccept: 'application/json',
      transferCache: false,
    }).pipe(
      map((result): NavigationAuditEventPageResult => ({
        ...result,
        data: result.data as NavigationAuditEventListDto[] | undefined,
      })),
    );
  }
}
