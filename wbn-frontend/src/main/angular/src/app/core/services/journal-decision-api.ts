import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { map } from 'rxjs';
import { JournalDecisionBenchmarks } from './journal-decision-benchmarks';
import {
  DecisionValueState,
  JournalDecisionAnnual,
  MniswRatingView,
} from './journal-decision-annual';
import {
  CardinalityView,
  CategoryView,
  CountryView,
  DescriptionView,
  InstitutionView,
  JournalView,
  LanguageView,
  PaidArticleView,
  PersonView,
} from '../backend';

export interface JournalDecisionIdentity {
  id: number;
  titleOriginal?: string | null;
  titleEnglish?: string | null;
  pissn?: string | null;
  eissn?: string | null;
  website?: string | null;
  cover?: string | null;
  country?: CountryView | null;
  languages?: LanguageView[] | null;
  publishers?: string[] | null;
  descriptions?: DescriptionView[] | null;
  cardinality?: CardinalityView | null;
  titleShort?: string | null;
  kind?: JournalView.KindEnum | null;
  printedVersion?: boolean | null;
  electronicVersion?: boolean | null;
  paidArticle?: PaidArticleView | null;
  scientificCategory?: CategoryView | null;
  scientificCategories?: CategoryView[] | null;
  trCategory?: CategoryView | null;
  trCategories?: CategoryView[] | null;
  ajscCategory?: CategoryView | null;
  ajscCategories?: CategoryView[] | null;
  editorInChiefs?: PersonView[] | null;
  deputyEditorInChiefs?: PersonView[] | null;
  editorialSecretaries?: PersonView[] | null;
  editors?: InstitutionView[] | null;
  publisherDetails?: InstitutionView[] | null;
  affiliationName?: string | null;
}

export interface JournalDecisionView {
  surveyYear: number;
  annualStatus:
    | 'AVAILABLE'
    | 'MISSING_SURVEY'
    | 'NOT_EVALUATED'
    | 'NOT_INDEXED'
    | 'UNAVAILABLE'
    | 'DATA_UPDATING'
    | 'DATA_NOT_READY';
  availableSurveyYears: number[];
  identity: JournalDecisionIdentity;
  icv?: number | null;
  icvStatus: DecisionValueState;
  mnisw: MniswRatingView | null;
  icvHistory: { year: number; status: 'INDEXED' | 'NOT_INDEXED'; value: number | null }[];
  annual: JournalDecisionAnnual | null;
  benchmarks: JournalDecisionBenchmarks | null;
  benchmarksVisible: boolean;
}

/**
 * Odczytuje Kartę, sprawdza jej identyfikator oraz wspólny rocznik ankiety i porównań.
 * Brak jawnego zezwolenia serwera oznacza ukrycie benchmarków, także podczas zmiany wersji API.
 * Gotowość danych i prezentacja porównań są niezależne, dlatego odpowiedź nie korzysta z pamięci transferu SSR.
 */
@Injectable({ providedIn: 'root' })
export class JournalDecisionApi {
  private readonly http = inject(HttpClient);

  get(id: number, surveyYear?: number) {
    return this.http
      .get<JournalDecisionView>(`${environment.apiBasePath}/api/journal/journal/${id}/decision`, {
        params: surveyYear === undefined ? {} : { surveyYear },
        // Odpowiedź zależy od uprawnień i aktualnej gotowości analityki; nie może pochodzić z transferu SSR.
        transferCache: false,
      })
      .pipe(
        map((data) => {
          if (
            data.identity.id !== id ||
            (surveyYear !== undefined && data.surveyYear !== surveyYear) ||
            (data.annual && data.annual.surveyYear !== data.surveyYear) ||
            (data.benchmarks && data.benchmarks.surveyYear !== data.surveyYear)
          ) {
            throw new Error('Inconsistent decision card response');
          }
          return { ...data, benchmarksVisible: data.benchmarksVisible === true };
        }),
      );
  }
}
