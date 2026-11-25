import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, tap } from 'rxjs';
import { JournalListQuery, PageResultJournalListView } from '../../../core/backend';
import { JournalSearchAnalyticsState } from '../../../core/services/journal-list-state';
import { NavigationAuditService } from '../../../core/services/navigation-audit.service';

export const JOURNAL_ANALYTICS_CRITERIA = [
  'surveyYear',
  'journalTitle',
  'issn',
  'publisher',
  'countryCodes',
  'languageCodes',
  'disciplineCodes',
  'ratingStatus',
  'icvFrom',
  'icvTo',
  'mniswOnly',
  'mniswDisciplineCodes',
  'mniswPointsFrom',
  'mniswPointsTo',
  'reviewDaysFrom',
  'reviewDaysTo',
  'initialAssessmentDaysFrom',
  'initialAssessmentDaysTo',
  'publicationDaysFrom',
  'publicationDaysTo',
  'rejectionRateFrom',
  'rejectionRateTo',
  'authorForeignAffiliationPercentageFrom',
  'authorForeignAffiliationPercentageTo',
  'reviewerForeignAffiliationPercentageFrom',
  'reviewerForeignAffiliationPercentageTo',
  'doiOnly',
  'editorialSystemOnly',
  'newsletterOnly',
  'socialMediaOnly',
  'antiPlagiarismOnly',
  'aopOnly',
  'repositoryIds',
  'licenseCodes',
  'accessModels',
  'frequencyCodes',
  'reviewModels',
  'firstIssueYearFrom',
  'firstIssueYearTo',
  'otherFeesFreeOnly',
  'openAccessFeeFreeOnly',
  'openAccessFeeMaxPln',
  'publicationFeeFreeOnly',
  'publicationFeeMaxPln',
  'rapidReviewFeeFreeOnly',
  'rapidReviewFeeMaxPln',
  'submissionFeeFreeOnly',
  'submissionFeeMaxPln',
] as const satisfies readonly (keyof JournalListQuery)[];

/**
 * Kopiuje wyłącznie dozwolone, zastosowane kryteria; pomija puste wartości i wyłączone flagi, ale zachowuje zero.
 * Roboczy formularz nie powinien być źródłem danych przekazywanych do tej funkcji.
 */
export function journalAnalyticsCriteria(query: JournalListQuery): Record<string, unknown> {
  return structuredClone(
    Object.fromEntries(
      JOURNAL_ANALYTICS_CRITERIA.flatMap((key) => {
        const value = query[key];
        return value == null ||
          value === false ||
          value === '' ||
          (Array.isArray(value) && !value.length)
          ? []
          : [[key, value]];
      }),
    ),
  );
}

/**
 * Rozdziela intencję Szukaj/czyszczenia od kolejnych prób pobrania wyników tej samej operacji.
 * Koreluje stronę, retry i przejście do P2 bez ponownego naliczania wyszukania lub pustego wyniku.
 */
@Injectable()
export class JournalSearchAnalytics {
  private readonly audit = inject(NavigationAuditService);
  private operation?: JournalSearchAnalyticsState;

  /**
   * Nadaje identyfikator nowej operacji lub odtwarza wcześniejszy po powrocie z P2.
   * Tylko jawne SEARCH i CLEAR emitują nową intencję użytkownika; wejście i powrót jej nie zastępują.
   */
  begin(
    query: JournalListQuery,
    origin: JournalSearchAnalyticsState['origin'],
    restored?: JournalSearchAnalyticsState,
  ): void {
    this.operation = restored
      ? { ...restored }
      : { operationId: crypto.randomUUID(), origin, emptyReported: false, attempt: 0 };
    if (origin === 'SEARCH' || origin === 'CLEAR') {
      this.audit.track({
        ...this.context(),
        eventType: origin === 'SEARCH' ? 'JOURNAL_SEARCH' : 'JOURNAL_FILTERS_CLEAR',
        properties: this.properties(query, this.operation),
      });
    }
  }

  /** Zachowuje korelację i stan deduplikacji na czas przejścia do P2, bez utrwalania samych wyników. */
  snapshot(): JournalSearchAnalyticsState | undefined {
    return this.operation ? { ...this.operation } : undefined;
  }

  /** Rejestruje intencję otwarcia karty z zastosowanymi kryteriami, a nie potwierdzenie załadowania P2. */
  cardOpen(query: JournalListQuery, journalId: number): void {
    if (!this.operation) this.begin(query, 'INITIAL');
    this.audit.track({
      ...this.context(),
      eventType: 'JOURNAL_CARD_OPEN',
      resource: { type: 'journal', id: journalId.toString() },
      properties: this.properties(query, this.operation!),
    });
  }

  issuesClick(query: JournalListQuery, journalId: number): void {
    if (!this.operation) this.begin(query, 'INITIAL');
    this.audit.track({
      ...this.context(),
      eventType: 'JOURNAL_ISSUES_CLICK',
      resource: { type: 'journal', id: journalId.toString() },
      properties: this.properties(query, this.operation!),
    });
    this.audit.flush();
  }

  /**
   * Numeruje próby faktycznego pobrania, zapisuje sukces lub błąd i zgłasza zero najwyżej raz na operację.
   * Odpowiedź poprzedniej operacji nie może dopisać zdarzenia do nowszego wyszukiwania.
   */
  load(
    query: JournalListQuery,
    loader: () => Observable<PageResultJournalListView>,
  ): Observable<PageResultJournalListView> {
    return defer(() => {
      if (!this.operation) this.begin(query, 'INITIAL');
      const operation = this.operation!;
      const properties = { ...this.properties(query, operation), attempt: ++operation.attempt };
      const context = this.context();
      const startedAt = performance.now();
      return loader().pipe(
        tap({
          next: (page) => {
            if (this.operation !== operation) return;
            const result = {
              ...properties,
              resultCount: page.totalElements ?? page.data?.length ?? 0,
            };
            this.audit.track({
              ...context,
              eventType: 'JOURNAL_SEARCH_RESULT',
              success: true,
              durationMs: Math.round(performance.now() - startedAt),
              properties: result,
            });
            if (page.totalElements === 0 && !operation.emptyReported) {
              operation.emptyReported = true;
              this.audit.track({
                ...context,
                eventType: 'JOURNAL_SEARCH_EMPTY',
                success: true,
                properties: result,
              });
            }
          },
          error: (error: unknown) => {
            if (this.operation !== operation) return;
            this.audit.track({
              ...context,
              eventType: 'JOURNAL_SEARCH_RESULT',
              success: false,
              durationMs: Math.round(performance.now() - startedAt),
              properties,
              statusCode:
                error instanceof HttpErrorResponse && error.status >= 100 && error.status <= 599
                  ? error.status
                  : undefined,
            });
          },
        }),
      );
    });
  }

  private properties(
    query: JournalListQuery,
    operation: JournalSearchAnalyticsState,
  ): Record<string, unknown> {
    return {
      schemaVersion: 1,
      searchView: query.searchView ?? 'ADVANCED',
      operationId: operation.operationId,
      origin: operation.origin,
      criteria: journalAnalyticsCriteria(query),
      page: query.page ?? 0,
      size: query.size ?? 10,
      sortBy: query.sortBy ?? JournalListQuery.SortByEnum.SortByTitleAsc,
    };
  }

  private context() {
    const view = this.audit.captureView();
    return {
      ...(view.pageKey === 'institution.journals.list' ? view : {}),
      pageKey: 'institution.journals.list',
      routeTemplate: '/institution/user/journals',
      url: '/institution/user/journals',
    };
  }
}
