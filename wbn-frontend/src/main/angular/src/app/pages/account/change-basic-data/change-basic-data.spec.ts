import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { UserBasicDataForm, UserProfileResponse, UserProfileService } from '../../../core/backend';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast-service';
import { translocoTestingModule } from '../../../testing/transloco-testing-module';
import { ChangeBasicData } from './change-basic-data';

describe('ChangeBasicData', () => {
  let component: ChangeBasicData;
  let fixture: ComponentFixture<ChangeBasicData>;
  let userProfileService: {
    ownBasicDataForm: ReturnType<typeof vi.fn>;
    updateOwnBasicData: ReturnType<typeof vi.fn>;
  };
  let authService: {
    loadUserProfile: ReturnType<typeof vi.fn>;
  };
  let toastService: {
    show: ReturnType<typeof vi.fn>;
  };
  let router: Router;
  const form: UserBasicDataForm = {
    firstName: 'Jan',
    lastName: 'Kowalski',
    orcid: '0000-0000-0000-000X',
  };
  const profile: UserProfileResponse = {
    ...form,
    institutionType: UserProfileResponse.InstitutionTypeEnum.ResearchUnit,
  };

  beforeEach(async () => {
    userProfileService = {
      ownBasicDataForm: vi.fn(() => of(form)),
      updateOwnBasicData: vi.fn(() => of(profile)),
    };
    authService = {
      loadUserProfile: vi.fn(() => of(profile)),
    };
    toastService = {
      show: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ChangeBasicData, translocoTestingModule],
      providers: [
        provideRouter([]),
        { provide: UserProfileService, useValue: userProfileService },
        { provide: AuthService, useValue: authService },
        { provide: ToastService, useValue: toastService },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(ChangeBasicData);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should load backend form data into the form', () => {
    expect(component.form.getRawValue()).toEqual({
      firstName: 'Jan',
      lastName: 'Kowalski',
      orcid: '0000-0000-0000-000X',
    });
    expect(userProfileService.ownBasicDataForm).toHaveBeenCalled();
    expect(authService.loadUserProfile).not.toHaveBeenCalled();
    expect(component.loadState()).toBe('success');
  });

  it('should not submit invalid form', () => {
    component.form.controls.firstName.setValue('   ');

    component.save();

    expect(userProfileService.updateOwnBasicData).not.toHaveBeenCalled();
    expect(component.form.controls.firstName.touched).toBe(true);
    expect(component.validationError('firstName')).toBe('page.account.change-basic-data.validation.first-name.required');
  });

  it('should validate ORCID before submit', () => {
    component.form.controls.orcid.setValue('bad-orcid');

    component.save();

    expect(userProfileService.updateOwnBasicData).not.toHaveBeenCalled();
    expect(component.form.controls.orcid.touched).toBe(true);
    expect(component.validationError('orcid')).toBe('page.account.change-basic-data.validation.orcid.pattern');
  });

  it('should submit normalized user data and refresh profile', () => {
    component.form.setValue({
      firstName: ' Anna ',
      lastName: ' Nowak ',
      orcid: '0000-0000-0000-000x',
    });

    component.save();

    expect(userProfileService.updateOwnBasicData).toHaveBeenCalledWith({
      firstName: 'Anna',
      lastName: 'Nowak',
      orcid: '0000-0000-0000-000X',
    });
    expect(authService.loadUserProfile).toHaveBeenCalled();
    expect(toastService.show).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/institution/user/dashboard']);
  });

  it('should apply backend field errors', () => {
    userProfileService.updateOwnBasicData.mockReturnValueOnce(throwError(() => new HttpErrorResponse({
      status: 400,
      error: {
        message: 'Request validation failed',
        fieldErrors: {
          orcid: 'Invalid ORCID',
        },
      },
    })));

    component.save();

    expect(component.fieldError('orcid')).toBe('Invalid ORCID');
    expect(component.form.controls.orcid.errors?.['backend']).toBe(true);
    expect(component.errorMessage()).toBe('Request validation failed');
  });

  it('should show load error when backend rejects form loading', async () => {
    fixture.destroy();
    userProfileService.ownBasicDataForm.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 403 })));

    fixture = TestBed.createComponent(ChangeBasicData);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.loadState()).toBe('error');
  });
});
