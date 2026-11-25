import {ComponentFixture, TestBed} from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import {ActivatedRoute, convertToParamMap, provideRouter} from '@angular/router';
import {of, throwError} from 'rxjs';
import {TranslocoTestingModule} from '@jsverse/transloco';

import {RegistrationService} from '../../../core/backend';
import {RegistrationActivationPage} from './registration-activation-page';

const translocoTestingModule = TranslocoTestingModule.forRoot({
  langs: {
    pl: {
      page: {
        home: {
          'registration-activation': {
            'activation-error': 'Aktywacja konta nie powiodła się. Link aktywacyjny jest nieprawidłowy.',
            'activation-token-already-used': 'Ten link aktywacyjny został już wykorzystany. Konto nie zostało aktywowane.',
            status: {
              'activation-link-expired-new-link-sent': {
                title: 'Link aktywacyjny wygasł',
                message: 'Link aktywacyjny wygasł. Konto nie zostało aktywowane. Wysłaliśmy nowy link aktywacyjny na adres e-mail konta.',
              },
            },
          },
        },
      },
    },
  },
  translocoConfig: {
    availableLangs: ['pl'],
    defaultLang: 'pl',
    reRenderOnLangChange: true,
    prodMode: true,
  },
  preloadLangs: true,
});

describe('RegistrationActivationPage', () => {
  let component: RegistrationActivationPage;
  let fixture: ComponentFixture<RegistrationActivationPage>;
  let registrationService: {
    activateRegistration: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    registrationService = {
      activateRegistration: vi.fn(() => of({status: 'ACCOUNT_ACTIVATED'})),
    };

    await TestBed.configureTestingModule({
      imports: [RegistrationActivationPage, translocoTestingModule],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({token: 'activation-token'}),
            },
          },
        },
        {
          provide: RegistrationService,
          useValue: registrationService,
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegistrationActivationPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show an error when account activation fails', async () => {
    registrationService.activateRegistration.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 400,
      error: {
        code: 'INVALID_REQUEST',
      },
    })));
    fixture.destroy();

    fixture = TestBed.createComponent(RegistrationActivationPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.errorMessage()).toBe('Aktywacja konta nie powiodła się. Link aktywacyjny jest nieprawidłowy.');
    expect(fixture.nativeElement.querySelector('.alert')?.classList).toContain('alert-danger');
  });

  it('should show a translated message when the activation token was already used', async () => {
    registrationService.activateRegistration.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 400,
      error: {
        code: 'ACTIVATION_TOKEN_ALREADY_USED',
        message: 'Activation token was already used.',
      },
    })));
    fixture.destroy();

    fixture = TestBed.createComponent(RegistrationActivationPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.errorMessage()).toBe('Ten link aktywacyjny został już wykorzystany. Konto nie zostało aktywowane.');
  });

  it('should show a warning when the activation link has expired', async () => {
    registrationService.activateRegistration.mockReturnValueOnce(of({
      status: 'ACTIVATION_LINK_EXPIRED_NEW_LINK_SENT',
    }));
    fixture.destroy();

    fixture = TestBed.createComponent(RegistrationActivationPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();

    const alert = fixture.nativeElement.querySelector('.alert');

    expect(component.errorMessage()).toBeNull();
    expect(alert?.classList).toContain('alert-warning');
    expect(alert?.textContent).toContain('Link aktywacyjny wygasł');
    expect(alert?.textContent).toContain('Konto nie zostało aktywowane. Wysłaliśmy nowy link aktywacyjny na adres e-mail konta.');
  });
});
