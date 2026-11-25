import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of } from 'rxjs';
import { EmailTemplateDto, EmailTemplatesService } from '../../../core/backend';
import { ToastService } from '../../../core/services/toast-service';
import { EmailTemplateEdit } from './email-template-edit';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('EmailTemplateEdit', () => {
  let component: EmailTemplateEdit;
  let fixture: ComponentFixture<EmailTemplateEdit>;
  let emailTemplatesService: {
    findEmailTemplate: ReturnType<typeof vi.fn>;
    updateEmailTemplate: ReturnType<typeof vi.fn>;
  };
  let router: Router;

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
      updateEmailTemplate: vi.fn(() => of({
        ...activationTemplate,
        subject: 'Nowy temat',
      })),
    };

    await TestBed.configureTestingModule({
      imports: [EmailTemplateEdit],
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
        { provide: ToastService, useValue: { show: vi.fn() } },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(EmailTemplateEdit);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load selected template into form', () => {
    expect(emailTemplatesService.findEmailTemplate).toHaveBeenCalledWith(
      1,
      'body',
      false,
      { transferCache: false },
    );
    expect(component.form.controls.subject.value).toBe('Aktywacja konta');
  });

  it('should save selected template and navigate to details', async () => {
    component.form.controls.subject.setValue('Nowy temat');

    component.save();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(emailTemplatesService.updateEmailTemplate).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        name: 'Link aktywacyjny',
        language: 'pl',
        subject: 'Nowy temat',
        htmlBody: '<p>Aktywuj konto</p>',
        textBody: 'Aktywuj konto',
        state: EmailTemplateDto.StateEnum.Active,
      }),
      'body',
      false,
      { transferCache: false },
    );
    expect(router.navigate).toHaveBeenCalledWith(['/system/admin/email-templates', 1]);
  });
});
