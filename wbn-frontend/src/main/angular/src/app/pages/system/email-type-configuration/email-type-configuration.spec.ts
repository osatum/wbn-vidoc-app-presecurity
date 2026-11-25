import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of } from 'rxjs';
import { EmailTemplateDto, EmailTemplatesService } from '../../../core/backend';
import { EmailTypeConfiguration } from './email-type-configuration';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('EmailTypeConfiguration', () => {
  let component: EmailTypeConfiguration;
  let fixture: ComponentFixture<EmailTypeConfiguration>;
  let emailTemplatesService: {
    findEmailTemplates: ReturnType<typeof vi.fn>;
  };

  const activationTemplate: EmailTemplateDto = {
    id: 1,
    name: 'Link aktywacyjny',
    emailType: EmailTemplateDto.EmailTypeEnum.AccountActivationLink,
    language: 'pl',
    subject: 'Aktywacja konta',
    htmlBody: '<p>Aktywuj konto</p>',
    textBody: 'Aktywuj konto',
    state: EmailTemplateDto.StateEnum.Active,
  };

  const inactiveTemplate: EmailTemplateDto = {
    id: 2,
    name: 'Konto aktywne',
    emailType: EmailTemplateDto.EmailTypeEnum.AccountActivated,
    language: 'pl',
    subject: 'Konto aktywne',
    htmlBody: '<p>Konto aktywne</p>',
    state: EmailTemplateDto.StateEnum.Inactive,
  };

  beforeEach(async () => {
    emailTemplatesService = {
      findEmailTemplates: vi.fn(() => of([activationTemplate, inactiveTemplate])),
    };

    await TestBed.configureTestingModule({
      imports: [EmailTypeConfiguration],
      providers: [
        provideRouter([]),
        provideTransloco({
          config: {
            availableLangs: ['pl'],
            defaultLang: 'pl',
            prodMode: true,
          },
          loader: TestingTranslocoLoader,
        }),
        { provide: EmailTemplatesService, useValue: emailTemplatesService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EmailTypeConfiguration);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should build rows for every email type', () => {
    const rows = component.rows();

    expect(emailTemplatesService.findEmailTemplates).toHaveBeenCalledTimes(1);
    expect(rows.map((row) => row.emailType)).toEqual(Object.values(EmailTemplateDto.EmailTypeEnum));
  });

  it('should mark configured, inactive and missing types', () => {
    const activationRow = component.rows().find((row) => row.emailType === EmailTemplateDto.EmailTypeEnum.AccountActivationLink);
    const activatedRow = component.rows().find((row) => row.emailType === EmailTemplateDto.EmailTypeEnum.AccountActivated);
    const waitingRow = component.rows().find((row) => row.emailType === EmailTemplateDto.EmailTypeEnum.AccountWaitingForConfirmation);

    expect(activationRow?.activeTemplate).toEqual(activationTemplate);
    expect(component.status(activationRow!)).toBe('configured');
    expect(component.status(activatedRow!)).toBe('inactive');
    expect(component.status(waitingRow!)).toBe('missing');
  });
});
