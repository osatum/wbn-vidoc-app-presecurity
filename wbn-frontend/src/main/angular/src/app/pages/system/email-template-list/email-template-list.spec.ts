import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of } from 'rxjs';
import { EmailTemplateDto, EmailTemplatesService } from '../../../core/backend';
import { EmailTemplateList } from './email-template-list';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('EmailTemplateList', () => {
  let component: EmailTemplateList;
  let fixture: ComponentFixture<EmailTemplateList>;
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

  beforeEach(async () => {
    emailTemplatesService = {
      findEmailTemplates: vi.fn(() => of([activationTemplate])),
    };

    await TestBed.configureTestingModule({
      imports: [EmailTemplateList],
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

    fixture = TestBed.createComponent(EmailTemplateList);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load templates', () => {
    expect(emailTemplatesService.findEmailTemplates).toHaveBeenCalledTimes(1);
    expect(component.templates()).toEqual([activationTemplate]);
  });
});
