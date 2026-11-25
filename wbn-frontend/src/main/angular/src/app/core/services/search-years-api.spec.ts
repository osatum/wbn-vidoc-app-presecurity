import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ConfigurationService, JournalService, QuestionnaireService } from '../backend';

describe('Published search year API', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads public journal links from backend configuration without session parameters', () => {
    const configuration = { issuesUrlTemplate: 'https://journals.example.test/issues?journalId={journalId}' };
    TestBed.inject(ConfigurationService).getJournalLinks().subscribe((links) => {
      expect(links).toEqual(configuration);
    });
    const request = http.expectOne((value) => value.url.endsWith('/api/configuration/journalLinks'));
    expect(request.request.params.keys()).toEqual([]);
    expect(request.request.transferCache).toBe(false);
    request.flush(configuration);
  });

  it('loads the published list separately from ICV presentation years', () => {
    TestBed.inject(ConfigurationService).getSearchYears().subscribe((configuration) => {
      expect(configuration).toEqual({ defaultYear: 2025, availableYears: [2025, 2024] });
    });
    http.expectOne((request) => request.url.endsWith('/api/configuration/searchYears'))
      .flush({ defaultYear: 2025, availableYears: [2025, 2024] });
  });

  it('passes the selected year to details, metadata, questionnaire and report', () => {
    TestBed.inject(JournalService).getJournal(123, 'body', false, { surveyYear: 2024 }).subscribe();
    TestBed.inject(JournalService).getJournalSearchMetadata('body', false, { searchView: 'ADVANCED', surveyYear: 2024 }).subscribe();
    TestBed.inject(QuestionnaireService).findCurrentQuestionnaire(123, 'body', false, { surveyYear: 2024 }).subscribe();
    TestBed.inject(QuestionnaireService).generateCurrentQuestionnaireReport(123, 'en', 'body', false,
      { surveyYear: 2024, httpHeaderAccept: 'application/vnd.ms-excel' }).subscribe();
    for (const request of http.match(() => true)) {
      expect(request.request.params.get('surveyYear')).toBe('2024');
      if (request.request.url.endsWith('/report')) {
        expect(request.request.params.get('language')).toBe('en');
        request.flush(new Blob(['report']));
      } else request.flush({});
    }
  });
});
