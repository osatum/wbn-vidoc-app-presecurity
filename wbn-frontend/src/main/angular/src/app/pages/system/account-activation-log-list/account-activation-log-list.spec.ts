import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of } from 'rxjs';

import { AccountActivationLogListQuery, AccountActivationsService } from '../../../core/backend';
import { AccountActivationLogList } from './account-activation-log-list';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('AccountActivationLogList', () => {
  let component: AccountActivationLogList;
  let fixture: ComponentFixture<AccountActivationLogList>;
  let accountActivationsService: {
    findActivationLogs: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    accountActivationsService = {
      findActivationLogs: vi.fn(() => of({ data: [], totalElements: 0, totalPages: 0 })),
    };

    await TestBed.configureTestingModule({
      imports: [AccountActivationLogList],
      providers: [
        provideTransloco({
          config: {
            availableLangs: ['pl'],
            defaultLang: 'pl',
            prodMode: true,
          },
          loader: TestingTranslocoLoader,
        }),
        { provide: AccountActivationsService, useValue: accountActivationsService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountActivationLogList);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should search activation logs with applied filters', async () => {
    component.filterForm.setValue({
      name: 'Research',
      email: 'jan@example.edu',
      ipAddress: '192.0.2',
      userAgent: 'Chrome',
      status: AccountActivationLogListQuery.StatusEnum.SuccessAutoActivated,
      attemptedFrom: '2026-06-01',
      attemptedTo: '2026-06-12',
    });

    component.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(accountActivationsService.findActivationLogs).toHaveBeenCalledTimes(2);
    expect(accountActivationsService.findActivationLogs).toHaveBeenLastCalledWith(
      expect.objectContaining({
        name: 'Research',
        email: 'jan@example.edu',
        ipAddress: '192.0.2',
        userAgent: 'Chrome',
        status: AccountActivationLogListQuery.StatusEnum.SuccessAutoActivated,
        attemptedFrom: expect.any(String),
        attemptedTo: expect.any(String),
      }),
    );
  });
});
