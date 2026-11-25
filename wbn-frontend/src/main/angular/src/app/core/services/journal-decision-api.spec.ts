import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { JournalDecisionApi, JournalDecisionView } from './journal-decision-api';
import { JournalDecisionBenchmarks } from './journal-decision-benchmarks';

describe('JournalDecisionApi annual context', () => {
  let api: JournalDecisionApi;
  let http: HttpTestingController;
  const card: JournalDecisionView = {
    surveyYear: 2025,
    annualStatus: 'MISSING_SURVEY',
    availableSurveyYears: [2024],
    identity: { id: 123 },
    icv: null,
    icvStatus: 'MISSING',
    mnisw: { year: 2024, listed: true, points: 70, disciplines: [], status: 'AVAILABLE' },
    icvHistory: [{ year: 2022, status: 'INDEXED', value: 0 }],
    annual: null,
    benchmarks: null,
    benchmarksVisible: true,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(JournalDecisionApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('defaults to hidden comparisons when the server does not provide the visibility flag', async () => {
    const result = firstValueFrom(api.get(123, 2025));
    http
      .expectOne((value) => value.url.endsWith('/123/decision'))
      .flush({ ...card, benchmarksVisible: undefined });
    expect((await result).benchmarksVisible).toBe(false);
  });

  it('keeps the requested year on a limited card and the independent MNiSW year and real history', async () => {
    const result = firstValueFrom(api.get(123, 2025));
    const request = http.expectOne((value) => value.url.endsWith('/123/decision'));
    expect(request.request.params.get('surveyYear')).toBe('2025');
    request.flush(card);
    expect(await result).toEqual(card);
  });

  it('leaves the default year to the server when there is no list or history context', async () => {
    const result = firstValueFrom(api.get(123));
    const request = http.expectOne((value) => value.url.endsWith('/123/decision'));
    expect(request.request.params.has('surveyYear')).toBe(false);
    request.flush(card);
    expect((await result).surveyYear).toBe(2025);
  });

  it('preserves raw benchmark values, local availability and shared axes with their build identifier', async () => {
    const unavailable = { status: 'UNAVAILABLE', value: null } as const;
    const benchmarks: JournalDecisionBenchmarks = {
      surveyYear: 2025,
      importId: '00000000-0000-0000-0000-000000000001',
      groupId: 1008,
      icv: {
        status: 'AVAILABLE',
        value: { lowerPercent: 100 / 30, equalPercent: 0, allEqual: false },
      },
      times: {
        initialAssessment: unavailable,
        review: {
          status: 'AVAILABLE',
          value: { q1: 18.25, q3: 32.75, axis: { minimum: 13, maximum: 37 } },
        },
        publication: unavailable,
      },
      rejectionRate: { status: 'AVAILABLE', value: { q1: 8.25, q3: 22.75 } },
      international: {
        authors: { status: 'AVAILABLE', value: { median: 0.5 } },
        reviewers: unavailable,
        scientificCouncil: { status: 'AVAILABLE', value: { median: 100 } },
      },
      fees: [
        {
          category: 'SUBMISSION',
          comparison: { status: 'AVAILABLE', value: { chargedPercent: 50 } },
        },
      ],
    };
    const response: JournalDecisionView = {
      ...card,
      annualStatus: 'AVAILABLE',
      icv: 81.125,
      benchmarks,
    };
    const result = firstValueFrom(api.get(123, 2025));
    http.expectOne((value) => value.url.endsWith('/123/decision')).flush(response);
    expect(await result).toEqual(response);
  });

  it.each([
    { ...card, surveyYear: 2024 },
    { ...card, identity: { id: 456 } },
    { ...card, annual: { surveyYear: 2024 } },
    { ...card, benchmarks: { surveyYear: 2024 } },
  ])('rejects a response for another journal or year', async (response) => {
    const result = firstValueFrom(api.get(123, 2025));
    const assertion = expect(result).rejects.toThrow('Inconsistent decision card response');
    http.expectOne((value) => value.url.endsWith('/123/decision')).flush(response);
    await assertion;
  });

  it.each(['DATA_UPDATING', 'DATA_NOT_READY'] as const)(
    'keeps the explicit readiness status %s',
    async (annualStatus) => {
      const response: JournalDecisionView = { ...card, annualStatus, mnisw: null };
      const result = firstValueFrom(api.get(123, 2025));
      http.expectOne((value) => value.url.endsWith('/123/decision')).flush(response);
      expect(await result).toEqual(response);
    },
  );
});
