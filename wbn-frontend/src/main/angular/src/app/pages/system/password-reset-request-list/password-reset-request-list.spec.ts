import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of } from 'rxjs';

import { PasswordResetRequestListQuery, PasswordResetRequestsService } from '../../../core/backend';
import { PasswordResetRequestList } from './password-reset-request-list';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('PasswordResetRequestList', () => {
  let component: PasswordResetRequestList;
  let fixture: ComponentFixture<PasswordResetRequestList>;
  let passwordResetRequestsService: {
    findPasswordResetRequests: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    passwordResetRequestsService = {
      findPasswordResetRequests: vi.fn(() => of({ data: [], totalElements: 0, totalPages: 0 })),
    };

    await TestBed.configureTestingModule({
      imports: [PasswordResetRequestList],
      providers: [
        provideTransloco({
          config: {
            availableLangs: ['pl'],
            defaultLang: 'pl',
            prodMode: true,
          },
          loader: TestingTranslocoLoader,
        }),
        { provide: PasswordResetRequestsService, useValue: passwordResetRequestsService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PasswordResetRequestList);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should search password reset requests with applied filters', async () => {
    component.filterForm.setValue({
      name: 'Jan',
      email: 'jan@example.edu',
      ipAddress: '192.0.2',
      userAgent: 'Chrome',
      status: PasswordResetRequestListQuery.StatusEnum.Completed,
      requestedFrom: '2026-06-01',
      requestedTo: '2026-06-12',
    });

    component.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(passwordResetRequestsService.findPasswordResetRequests).toHaveBeenCalledTimes(2);
    expect(passwordResetRequestsService.findPasswordResetRequests).toHaveBeenLastCalledWith(
      expect.objectContaining({
        name: 'Jan',
        email: 'jan@example.edu',
        ipAddress: '192.0.2',
        userAgent: 'Chrome',
        status: PasswordResetRequestListQuery.StatusEnum.Completed,
        requestedFrom: expect.any(String),
        requestedTo: expect.any(String),
      }),
    );
  });
});
