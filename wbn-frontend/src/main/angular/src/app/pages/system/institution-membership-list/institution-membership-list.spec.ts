import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTransloco, TranslocoLoader, Translation} from '@jsverse/transloco';
import {of} from 'rxjs';

import {InstitutionMembershipListDto, InstitutionService} from '../../../core/backend';
import {ToastService} from '../../../core/services/toast-service';
import {InstitutionMembershipList} from './institution-membership-list';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('InstitutionMembershipList', () => {
  let component: InstitutionMembershipList;
  let fixture: ComponentFixture<InstitutionMembershipList>;
  let institutionService: {
    searchInstitutionMemberships: ReturnType<typeof vi.fn>;
    changeInstitutionMembershipState: ReturnType<typeof vi.fn>;
    confirmInstitutionMembership: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    institutionService = {
      searchInstitutionMemberships: vi.fn(() => of({data: [], totalElements: 0, totalPages: 0})),
      changeInstitutionMembershipState: vi.fn(() => of(undefined)),
      confirmInstitutionMembership: vi.fn(() => of({status: 'MEMBERSHIP_CONFIRMED'})),
    };

    await TestBed.configureTestingModule({
      imports: [InstitutionMembershipList],
      providers: [
        provideTransloco({
          config: {
            availableLangs: ['pl'],
            defaultLang: 'pl',
            prodMode: true,
          },
          loader: TestingTranslocoLoader,
        }),
        {provide: InstitutionService, useValue: institutionService},
        {provide: ToastService, useValue: {show: vi.fn()}},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InstitutionMembershipList);
    fixture.componentRef.setInput('institutionId', 1);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should reload memberships after confirmation', async () => {
    expect(institutionService.searchInstitutionMemberships).toHaveBeenCalledTimes(1);

    component.confirmMembership({
      id: 20,
      role: InstitutionMembershipListDto.RoleEnum.InstitutionToConfirm,
      state: InstitutionMembershipListDto.StateEnum.Active,
    });

    fixture.detectChanges();
    await fixture.whenStable();

    expect(institutionService.confirmInstitutionMembership).toHaveBeenCalledWith(1, 20);
    expect(institutionService.searchInstitutionMemberships).toHaveBeenCalledTimes(2);
  });
});
