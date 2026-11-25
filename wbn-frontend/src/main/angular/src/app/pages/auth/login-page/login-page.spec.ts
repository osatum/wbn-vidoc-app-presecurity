import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';
import { RedirectService } from '../../../core/services/redirect-service';
import { ToastService } from '../../../core/services/toast-service';
import { translocoTestingModule } from '../../../testing/transloco-testing-module';
import { LoginPage } from './login-page';

describe('LoginPage', () => {
  let component: LoginPage;
  let fixture: ComponentFixture<LoginPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginPage, translocoTestingModule],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            getPendingInstitutionsSnapshot: vi.fn(() => null),
            login: vi.fn(() => of({})),
          },
        },
        { provide: RedirectService, useValue: { redirectAfterLogin: vi.fn() } },
        { provide: ToastService, useValue: { show: vi.fn() } },
      ],
    })
    .compileComponents();

    fixture = TestBed.createComponent(LoginPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
