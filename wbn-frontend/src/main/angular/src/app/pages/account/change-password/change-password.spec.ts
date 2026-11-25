import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { UserProfileService } from '../../../core/backend';
import { HANDLE_UNAUTHORIZED_LOCALLY } from '../../../core/interceptors/auth-error-context';
import { translocoTestingModule } from '../../../testing/transloco-testing-module';
import { ChangePassword } from './change-password';

describe('ChangePassword', () => {
  let component: ChangePassword;
  let fixture: ComponentFixture<ChangePassword>;
  let userProfileService: {
    changeOwnPassword: ReturnType<typeof vi.fn>;
  };
  let activatedRoute: {
    snapshot: {
      data: Record<string, unknown>;
    };
  };

  beforeEach(async () => {
    userProfileService = {
      changeOwnPassword: vi.fn(() => of(null)),
    };
    activatedRoute = {
      snapshot: {
        data: {},
      },
    };

    await TestBed.configureTestingModule({
      imports: [ChangePassword, translocoTestingModule],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: activatedRoute },
        { provide: UserProfileService, useValue: userProfileService },
      ],
    }).compileComponents();

    await createComponent();
  });

  async function createComponent(routeData: Record<string, unknown> = {}): Promise<void> {
    activatedRoute.snapshot.data = routeData;
    fixture = TestBed.createComponent(ChangePassword);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should use institution view by default', () => {
    expect(component.viewMode()).toBe('institution');
    expect(component.titleKey()).toBe('page.account.change-password.institution.title');
  });

  it('should use system view from route data', async () => {
    fixture.destroy();

    await createComponent({
      returnUrl: '/system/admin/dashboard',
      viewMode: 'system',
    });

    expect(component.viewMode()).toBe('system');
    expect(component.returnUrl()).toBe('/system/admin/dashboard');
    expect(component.titleKey()).toBe('page.account.change-password.system.title');
  });

  it('should not submit when new passwords do not match', () => {
    component.form.controls.currentPassword.setValue('current-password');
    component.form.controls.password.setValue('new-password');
    component.form.controls.passwordRepeat.setValue('other-password');

    component.save();

    expect(userProfileService.changeOwnPassword).not.toHaveBeenCalled();
    expect(component.form.hasError('passwordMismatch')).toBe(true);
  });

  it('should submit password change and show success message on the page', () => {
    component.form.controls.currentPassword.setValue('current-password');
    component.form.controls.password.setValue('new-password');
    component.form.controls.passwordRepeat.setValue('new-password');

    component.save();

    expect(userProfileService.changeOwnPassword).toHaveBeenCalledWith({
      currentPassword: 'current-password',
      password: 'new-password',
      passwordRepeat: 'new-password',
    }, undefined, undefined, {
      context: expect.any(HttpContext),
    });
    const options = userProfileService.changeOwnPassword.mock.calls[0][3];
    expect(options.context.get(HANDLE_UNAUTHORIZED_LOCALLY)).toBe(true);
    expect(component.successMessage()).toBeTruthy();

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.alert-success')?.textContent)
      .toContain('page.account.change-password.save-success');
  });

  it('should show current password error on authentication failure', () => {
    userProfileService.changeOwnPassword.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 401,
      error: {
        code: 'AUTHENTICATION_FAILED',
        message: 'Invalid credentials',
        fieldErrors: {},
      },
    })));
    component.form.controls.currentPassword.setValue('bad-password');
    component.form.controls.password.setValue('new-password');
    component.form.controls.passwordRepeat.setValue('new-password');

    component.save();

    expect(component.fieldError('currentPassword')).toBeTruthy();
    expect(component.errorMessage()).toBeTruthy();
    expect(component.form.controls.currentPassword.errors?.['backend']).toBe(true);

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.alert-danger')?.textContent)
      .toContain('page.account.change-password.current-password-error');
  });
});
