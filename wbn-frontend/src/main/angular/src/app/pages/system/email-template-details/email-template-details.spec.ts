import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of } from 'rxjs';
import { EmailTemplateDto, EmailTemplatesService } from '../../../core/backend';
import { EmailTemplateDetails } from './email-template-details';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('EmailTemplateDetails', () => {
  let component: EmailTemplateDetails;
  let fixture: ComponentFixture<EmailTemplateDetails>;
  let emailTemplatesService: {
    findEmailTemplate: ReturnType<typeof vi.fn>;
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
      findEmailTemplate: vi.fn(() => of(activationTemplate)),
    };

    await TestBed.configureTestingModule({
      imports: [EmailTemplateDetails],
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
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ id: '1' }),
            },
          },
        },
        { provide: EmailTemplatesService, useValue: emailTemplatesService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EmailTemplateDetails);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load selected template', () => {
    expect(emailTemplatesService.findEmailTemplate).toHaveBeenCalledWith(
      1,
      'body',
      false,
      { transferCache: false },
    );
    expect(component.template()).toEqual(activationTemplate);
  });
});
