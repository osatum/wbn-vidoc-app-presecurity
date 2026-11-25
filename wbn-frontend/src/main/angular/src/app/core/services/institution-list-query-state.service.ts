import { Injectable, inject } from '@angular/core';
import { Observable, of, tap } from 'rxjs';

import { InstitutionListQuery, InstitutionService } from '../backend';

const STORAGE_KEY = 'institutionList.query';

@Injectable({
  providedIn: 'root',
})
export class InstitutionListQueryStateService {
  private readonly institutionService = inject(InstitutionService);

  getInitialQuery(): Observable<InstitutionListQuery> {
    const storedQuery = this.readStoredQuery();

    if (storedQuery) {
      return of(storedQuery);
    }

    return this.institutionService
      .getDefaultInstitutionListQuery()
      .pipe(tap((query) => this.saveQuery(query)));
  }

  saveQuery(query: InstitutionListQuery): void {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(this.normalizeQuery(query)));
    } catch {
      // Session storage can be unavailable in restricted browser contexts.
    }
  }

  private readStoredQuery(): InstitutionListQuery | undefined {
    try {
      const value = sessionStorage.getItem(STORAGE_KEY);

      if (!value) {
        return undefined;
      }

      const parsed: unknown = JSON.parse(value);

      if (!this.isRecord(parsed)) {
        this.removeStoredQuery();
        return undefined;
      }

      return this.normalizeQuery(parsed as InstitutionListQuery);
    } catch {
      this.removeStoredQuery();
      return undefined;
    }
  }

  private removeStoredQuery(): void {
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // Session storage can be unavailable in restricted browser contexts.
    }
  }

  private normalizeQuery(query: InstitutionListQuery): InstitutionListQuery {
    const normalized: Record<string, unknown> = { ...query };

    Object.entries(normalized).forEach(([key, value]) => {
      if (value === null) {
        delete normalized[key];
      }
    });

    if (!this.isInstitutionType(normalized['institutionType'])) {
      delete normalized['institutionType'];
    }

    return normalized as InstitutionListQuery;
  }

  private isInstitutionType(value: unknown): value is InstitutionListQuery.InstitutionTypeEnum {
    return Object.values(InstitutionListQuery.InstitutionTypeEnum).includes(
      value as InstitutionListQuery.InstitutionTypeEnum,
    );
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
  }
}
