import { Injectable, inject } from '@angular/core';
import { Observable, of, tap } from 'rxjs';

import { UserListQuery, UserService } from '../backend';

const STORAGE_KEY_PREFIX = 'userList.query';
const DEFAULT_SCOPE = 'global';

@Injectable({
  providedIn: 'root',
})
export class UserListQueryStateService {
  private readonly userService = inject(UserService);

  getInitialQuery(scope = DEFAULT_SCOPE): Observable<UserListQuery> {
    const storedQuery = this.readStoredQuery(scope);

    if (storedQuery) {
      return of(storedQuery);
    }

    return this.userService
      .getDefaultUserListQuery()
      .pipe(tap((query) => this.saveQuery(query, scope)));
  }

  saveQuery(query: UserListQuery, scope = DEFAULT_SCOPE): void {
    try {
      sessionStorage.setItem(this.storageKey(scope), JSON.stringify(this.normalizeQuery(query)));
    } catch {
      // Session storage can be unavailable in restricted browser contexts.
    }
  }

  private readStoredQuery(scope: string): UserListQuery | undefined {
    try {
      const value = sessionStorage.getItem(this.storageKey(scope));

      if (!value) {
        return undefined;
      }

      const parsed: unknown = JSON.parse(value);

      if (!this.isRecord(parsed)) {
        this.removeStoredQuery(scope);
        return undefined;
      }

      return this.normalizeQuery(parsed as UserListQuery);
    } catch {
      this.removeStoredQuery(scope);
      return undefined;
    }
  }

  private removeStoredQuery(scope: string): void {
    try {
      sessionStorage.removeItem(this.storageKey(scope));
    } catch {
      // Session storage can be unavailable in restricted browser contexts.
    }
  }

  private normalizeQuery(query: UserListQuery): UserListQuery {
    const normalized: Record<string, unknown> = { ...query };

    Object.entries(normalized).forEach(([key, value]) => {
      if (value === null) {
        delete normalized[key];
      }
    });

    if (!this.isPositiveInteger(normalized['institutionId'])) {
      delete normalized['institutionId'];
    }

    return normalized as UserListQuery;
  }

  private storageKey(scope: string): string {
    return `${STORAGE_KEY_PREFIX}.${scope || DEFAULT_SCOPE}`;
  }

  private isPositiveInteger(value: unknown): value is number {
    return typeof value === 'number' && Number.isInteger(value) && value > 0;
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
  }
}
