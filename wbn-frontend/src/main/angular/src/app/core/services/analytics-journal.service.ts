import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, shareReplay, throwError } from 'rxjs';

import { JournalService } from '../backend';
import type {
  AnalyticsContainerView,
  AnalyticsCountryView,
  AnalyticsJournalView,
  CountryView,
} from '../backend';

export type {
  AnalyticsContainerView,
  AnalyticsCountryView,
  AnalyticsJournalView,
  CountryView,
} from '../backend';

export type WorldGeoJson = {
  features?: WorldGeoJsonFeature[];
  [key: string]: unknown;
};

export type WorldGeoJsonFeature = {
  properties?: {
    name?: string;
    iso_a2?: string;
    postal?: string;
    [key: string]: unknown;
  };
  [key: string]: unknown;
};

@Injectable({
  providedIn: 'root',
})
export class AnalyticsJournalService {
  private readonly http = inject(HttpClient);
  private readonly journalService = inject(JournalService);
  private readonly analyticsCache = new Map<string, Observable<AnalyticsJournalView>>();
  private worldGeoJson$?: Observable<WorldGeoJson>;

  getAnalyticsJournal(journalId: number, surveyYear?: number): Observable<AnalyticsJournalView> {
    const key = `${journalId}:${surveyYear ?? 'default'}`;
    const cached = this.analyticsCache.get(key);
    if (cached !== undefined) {
      return cached;
    }

    const request$ = (surveyYear === undefined ? this.journalService.getAnalyticsJournal(journalId)
      : this.journalService.getAnalyticsJournal(journalId, 'body', false, { surveyYear })).pipe(
      catchError((error: unknown) => {
        this.analyticsCache.delete(key);
        return throwError(() => error);
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );

    this.analyticsCache.set(key, request$);
    return request$;
  }

  getWorldGeoJson(): Observable<WorldGeoJson> {
    if (this.worldGeoJson$ === undefined) {
      this.worldGeoJson$ = this.http.get<WorldGeoJson>('assets/maps/world.geo.json').pipe(
        catchError((error: unknown) => {
          this.worldGeoJson$ = undefined;
          return throwError(() => error);
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }

    return this.worldGeoJson$;
  }
}
