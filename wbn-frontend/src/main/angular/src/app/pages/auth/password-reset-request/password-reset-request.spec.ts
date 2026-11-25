import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { PasswordResetService } from '../../../core/backend';
import { translocoTestingModule } from '../../../testing/transloco-testing-module';
import { PasswordResetRequestPage } from './password-reset-request';

describe('PasswordResetRequestPage', () => {
  let component: PasswordResetRequestPage;
  let fixture: ComponentFixture<PasswordResetRequestPage>;
  let passwordResetService: {
    requestPasswordReset: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    passwordResetService = {
      requestPasswordReset: vi.fn(() => of({ accepted: true })),
    };

    await TestBed.configureTestingModule({
      imports: [PasswordResetRequestPage, translocoTestingModule],
      providers: [
        provideRouter([]),
        { provide: PasswordResetService, useValue: passwordResetService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PasswordResetRequestPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should submit e-mail', async () => {
    component.form.controls.email.setValue('Jan@Example.Edu');

    component.onSubmit();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(passwordResetService.requestPasswordReset).toHaveBeenCalledWith({ email: 'Jan@Example.Edu' });
    expect(component.isSuccess()).toBe(true);
  });
});
