import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';

import { AnalyticsJournalView, JournalService } from '../backend';
import { AnalyticsJournalService, WorldGeoJson } from './analytics-journal.service';

describe('AnalyticsJournalService', () => {
  let service: AnalyticsJournalService;
  let httpMock: HttpTestingController;
  let journalService: {
    getAnalyticsJournal: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    journalService = {
      getAnalyticsJournal: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: JournalService, useValue: journalService },
      ],
    });

    service = TestBed.inject(AnalyticsJournalService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should cache analytics request by journal id', () => {
    const response: AnalyticsJournalView = { journalId: 1 };
    const request = new Subject<AnalyticsJournalView>();
    const results: AnalyticsJournalView[] = [];

    journalService.getAnalyticsJournal.mockReturnValue(request.asObservable());

    service.getAnalyticsJournal(1).subscribe((value) => results.push(value));
    service.getAnalyticsJournal(1).subscribe((value) => results.push(value));
    request.next(response);
    request.complete();

    expect(journalService.getAnalyticsJournal).toHaveBeenCalledTimes(1);
    expect(journalService.getAnalyticsJournal).toHaveBeenCalledWith(1);
    expect(results).toEqual([response, response]);
  });

  it('should cache world geo json request', () => {
    const response: WorldGeoJson = { features: [] };
    const results: WorldGeoJson[] = [];

    service.getWorldGeoJson().subscribe((value) => results.push(value));
    service.getWorldGeoJson().subscribe((value) => results.push(value));

    const request = httpMock.expectOne('assets/maps/world.geo.json');
    request.flush(response);

    service.getWorldGeoJson().subscribe((value) => results.push(value));
    httpMock.expectNone('assets/maps/world.geo.json');

    expect(results).toEqual([response, response, response]);
  });

  it('keeps separate analytics requests for different survey years', () => {
    journalService.getAnalyticsJournal.mockReturnValue(new Subject<AnalyticsJournalView>());
    service.getAnalyticsJournal(1, 2024).subscribe();
    service.getAnalyticsJournal(1, 2025).subscribe();
    service.getAnalyticsJournal(1, 2024).subscribe();
    expect(journalService.getAnalyticsJournal).toHaveBeenCalledTimes(2);
    expect(journalService.getAnalyticsJournal).toHaveBeenCalledWith(1, 'body', false, { surveyYear: 2024 });
    expect(journalService.getAnalyticsJournal).toHaveBeenCalledWith(1, 'body', false, { surveyYear: 2025 });
  });
});
