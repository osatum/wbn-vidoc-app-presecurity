import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { PasswordResetService, PasswordResetTokenStatusDto } from '../../../core/backend';
import { translocoTestingModule } from '../../../testing/transloco-testing-module';
import { PasswordResetConfirmPage } from './password-reset-confirm';

describe('PasswordResetConfirmPage', () => {
  let component: PasswordResetConfirmPage;
  let fixture: ComponentFixture<PasswordResetConfirmPage>;
  let passwordResetService: {
    verifyPasswordResetToken: ReturnType<typeof vi.fn>;
    confirmPasswordReset: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    passwordResetService = {
      verifyPasswordResetToken: vi.fn(() => of({ status: PasswordResetTokenStatusDto.StatusEnum.Valid })),
      confirmPasswordReset: vi.fn(() => of({ status: PasswordResetTokenStatusDto.StatusEnum.Completed })),
    };

    await TestBed.configureTestingModule({
      imports: [PasswordResetConfirmPage, translocoTestingModule],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ token: 'reset-token' }),
            },
          },
        },
        { provide: PasswordResetService, useValue: passwordResetService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PasswordResetConfirmPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should confirm password reset', async () => {
    component.form.setValue({
      password: 'new-password',
      passwordRepeat: 'new-password',
    });

    component.onSubmit();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(passwordResetService.confirmPasswordReset).toHaveBeenCalledWith('reset-token', {
      password: 'new-password',
      passwordRepeat: 'new-password',
    });
    expect(component.success()).toBe(true);
  });
});
