import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DictionaryService, JournalSearchMetadata, JournalService, RepositoryDictionaryView } from '../backend';

describe('Journal search metadata API', () => {
  let service: JournalService;
  let requests: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(JournalService);
    requests = TestBed.inject(HttpTestingController);
  });

  afterEach(() => requests.verify());

  it('posts applied criteria to the counts endpoint without caching a transferred response', () => {
    service.getJournalSearchCounts({ searchView: 'ADVANCED', surveyYear: 2024, countryCodes: ['pl'] }).subscribe();
    const request = requests.expectOne((value) => value.url.endsWith('/api/journal/search-journals/counts'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ searchView: 'ADVANCED', surveyYear: 2024, countryCodes: ['pl'] });
    request.flush({ countryCounts: { pl: 2 } });
  });

  it('uses the generated GET endpoint without forwarding search criteria', () => {
    let metadata: JournalSearchMetadata | undefined;
    service
      .getJournalSearchMetadata('body', false, { transferCache: false })
      .subscribe((response) => (metadata = response));
    const request = requests.expectOne((value) =>
      value.url.endsWith('/api/journal/search-journals/metadata'),
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    expect(request.request.body).toBeNull();
    request.flush({ mniswPointClasses: [20, 70, 140], icvMin: 80.25, icvMax: 95.75,
      countryCounts: { pl: 2 }, languageCounts: { en: 3 }, disciplineCounts: { '42': 1 } });
    expect(metadata?.mniswPointClasses).toEqual([20, 70, 140]);
    expect(metadata?.icvMin).toBe(80.25);
    expect(metadata?.icvMax).toBe(95.75);
    expect(metadata?.countryCounts?.['pl']).toBe(2);
    expect(metadata?.languageCounts?.['en']).toBe(3);
    expect(metadata?.disciplineCounts?.['42']).toBe(1);
  });

  it('loads repository codes and names from the generated dictionary endpoint', () => {
    let repositories: RepositoryDictionaryView[] | undefined;
    TestBed.inject(DictionaryService).findRepositories().subscribe((result) => repositories = result);
    const request = requests.expectOne((value) => value.url.endsWith('/api/dictionaries/repositories'));
    expect(request.request.method).toBe('GET');
    request.flush([{ id: 10, name: 'DOAJ' }]);
    expect(repositories).toEqual([{ id: 10, name: 'DOAJ' }]);
  });
});
