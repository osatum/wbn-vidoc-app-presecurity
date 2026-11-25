import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';

import { translocoTestingModule } from '../../../../testing/transloco-testing-module';
import { AnalyticsJournalService } from '../../../../core/services/analytics-journal.service';
import { JournalQuestionnaireChartsComponent } from './journal-questionnaire-charts-component';

describe('JournalQuestionnaireComponent', () => {
  let component: JournalQuestionnaireChartsComponent;
  let fixture: ComponentFixture<JournalQuestionnaireChartsComponent>;
  let analyticsJournalService: {
    getAnalyticsJournal: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    analyticsJournalService = {
      getAnalyticsJournal: vi.fn().mockReturnValue(
        of({
          journalId: 1,
          articleScientificAmount: {
            items: [
              { year: 2022, value: 2 },
              { year: 2023, value: 3 },
            ],
          },
          articleManuscriptReceived: {
            items: [
              { year: 2021, value: 1 },
              { year: 2023, value: 4 },
            ],
          },
          authorUnique: {
            items: [{ year: 2023, value: 10 }],
          },
          reviewerForeign: {
            items: [{ year: 2023, value: 40 }],
          },
        }),
      ),
    };

    await TestBed.configureTestingModule({
      imports: [JournalQuestionnaireChartsComponent, translocoTestingModule],
      providers: [
        provideHttpClient(),
        { provide: AnalyticsJournalService, useValue: analyticsJournalService },
      ],
    })
      .overrideComponent(JournalQuestionnaireChartsComponent, {
        set: {
          template: '',
        },
      })
      .compileComponents();

    fixture = TestBed.createComponent(JournalQuestionnaireChartsComponent);
    fixture.componentRef.setInput('journalId', 1);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should request analytics for selected journal', () => {
    expect(analyticsJournalService.getAnalyticsJournal).toHaveBeenCalledWith(1, undefined);
  });
});
