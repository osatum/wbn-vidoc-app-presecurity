import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { UserProfileResponse } from '../../core/backend';
import { AuthService } from '../../core/services/auth.service';
import { translocoTestingModule } from '../../testing/transloco-testing-module';
import { PortalHeader } from './portal-header';

describe('PortalHeader', () => {
  let component: PortalHeader;
  let fixture: ComponentFixture<PortalHeader>;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PortalHeader, translocoTestingModule],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            userProfile$: of(null),
            resetPendingInstitution: vi.fn(),
            logout: vi.fn(),
            selectInstitution: vi.fn(() => of({})),
            getUserProfile: vi.fn(() => of(null)),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PortalHeader);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should navigate to own basic data form', () => {
    (component as unknown as { changeOwnBasicData: () => void }).changeOwnBasicData();

    expect(router.navigate).toHaveBeenCalledWith(['/institution/user/profile/basic-data']);
  });

  it('should allow basic data change only for research unit profiles', () => {
    const researchUnitProfile: UserProfileResponse = {
      institutionType: UserProfileResponse.InstitutionTypeEnum.ResearchUnit,
      userId: 1,
    };
    const systemProfile: UserProfileResponse = {
      institutionType: UserProfileResponse.InstitutionTypeEnum.SystemOperator,
    };

    expect((component as unknown as { canChangeOwnBasicData: (profile: UserProfileResponse) => boolean }).canChangeOwnBasicData(researchUnitProfile))
      .toBe(true);
    expect((component as unknown as { canChangeOwnBasicData: (profile: UserProfileResponse) => boolean }).canChangeOwnBasicData(systemProfile))
      .toBe(false);
  });
});
