import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of } from 'rxjs';

import {
  EmailMessageLogListDto,
  EmailMessageLogListQuery,
  EmailMessageResendResultDto,
  EmailMessagesService,
} from '../../../core/backend';
import { ToastService } from '../../../core/services/toast-service';
import { EmailMessageLogList } from './email-message-log-list';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('EmailMessageLogList', () => {
  let component: EmailMessageLogList;
  let fixture: ComponentFixture<EmailMessageLogList>;
  let emailMessagesService: {
    findEmailMessages: ReturnType<typeof vi.fn>;
    resendEmailMessage: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    emailMessagesService = {
      findEmailMessages: vi.fn(() => of({ data: [], totalElements: 0, totalPages: 0 })),
      resendEmailMessage: vi.fn(() => of({
        status: EmailMessageResendResultDto.StatusEnum.EmailResendQueued,
        queuedMessage: { id: 200 },
      })),
    };

    await TestBed.configureTestingModule({
      imports: [EmailMessageLogList],
      providers: [
        provideTransloco({
          config: {
            availableLangs: ['pl'],
            defaultLang: 'pl',
            prodMode: true,
          },
          loader: TestingTranslocoLoader,
        }),
        { provide: EmailMessagesService, useValue: emailMessagesService },
        { provide: ToastService, useValue: { show: vi.fn() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EmailMessageLogList);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should search email messages with applied filters', async () => {
    component.filterForm.setValue({
      name: 'Research',
      email: 'jan@example.edu',
      recipient: 'admin@example.edu',
      emailType: EmailMessageLogListQuery.EmailTypeEnum.AccountActivated,
      status: EmailMessageLogListQuery.StatusEnum.Sent,
      createdFrom: '2026-06-01',
      createdTo: '2026-06-12',
    });

    component.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(emailMessagesService.findEmailMessages).toHaveBeenCalledTimes(2);
    expect(emailMessagesService.findEmailMessages).toHaveBeenLastCalledWith(
      expect.objectContaining({
        name: 'Research',
        email: 'jan@example.edu',
        recipient: 'admin@example.edu',
        emailType: EmailMessageLogListQuery.EmailTypeEnum.AccountActivated,
        status: EmailMessageLogListQuery.StatusEnum.Sent,
        createdFrom: expect.any(String),
        createdTo: expect.any(String),
      }),
    );
  });

  it('should reload email messages after resend', async () => {
    expect(emailMessagesService.findEmailMessages).toHaveBeenCalledTimes(1);

    component.resendMessage({
      id: 100,
      status: EmailMessageLogListDto.StatusEnum.Failed,
    });

    fixture.detectChanges();
    await fixture.whenStable();

    expect(emailMessagesService.resendEmailMessage).toHaveBeenCalledWith(100);
    expect(emailMessagesService.findEmailMessages).toHaveBeenCalledTimes(2);
  });
});
