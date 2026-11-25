import { HttpClient } from '@angular/common/http';
import { Injectable, InjectionToken, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { OperatingContextDto, OperatingProfile, SearchView } from '../backend';

export const JOURNAL_SEARCH_VIEW = new InjectionToken<SearchView>('Journal search view', {
  providedIn: 'root',
  factory: () => 'ADVANCED',
});

/** Brak rozpoznanego kontekstu nie może domyślnie udostępnić linku do Karty. */
export const JOURNAL_DECISION_CARD_AVAILABLE = new InjectionToken<boolean>(
  'Journal decision card available',
  {
    providedIn: 'root',
    factory: () => false,
  },
);

/** Formularz BASIC w profilu BOTH nie odbiera Karty; nadal wymagana jest funkcja udostępniona przez backend. */
export function canOpenDecisionCard(context: OperatingContextDto): boolean {
  return (
    (context.effectiveProfile === 'ADVANCED' || context.effectiveProfile === 'BOTH') &&
    context.availableFeatures.includes('DECISION_CARD')
  );
}

export const OPERATING_PROFILES: readonly OperatingProfile[] = ['BASIC', 'ADVANCED', 'BOTH'];

export function availableSearchViews(context: OperatingContextDto): SearchView[] {
  return (['BASIC', 'ADVANCED'] as const).filter((view) =>
    context.availableFeatures.includes(view === 'BASIC' ? 'BASIC_SEARCH' : 'ADVANCED_SEARCH'),
  );
}

@Injectable({ providedIn: 'root' })
export class OperatingProfileService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiBasePath + '/api/configuration/operating-profile';

  currentContext() {
    return this.http.get<OperatingContextDto>(this.url, { transferCache: false });
  }

  defaultProfile() {
    return this.http.get<OperatingProfile>(this.url + '/default', { transferCache: false });
  }
}
