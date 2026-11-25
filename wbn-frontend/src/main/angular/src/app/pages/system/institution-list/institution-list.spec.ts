import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of } from 'rxjs';
import { InstitutionListViewDto, InstitutionService } from '../../../core/backend';

import { InstitutionList } from './institution-list';
import { InstitutionListQueryStateService } from '../../../core/services/institution-list-query-state.service';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('InstitutionList', () => {
  let component: InstitutionList;
  let fixture: ComponentFixture<InstitutionList>;
  let institutionService: {
    searchInstitutions: ReturnType<typeof vi.fn>;
    changeInstitutionState: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    institutionService = {
      searchInstitutions: vi.fn(() => of({ data: [], totalElements: 0, totalPages: 0 })),
      changeInstitutionState: vi.fn(() => of(undefined)),
    };

    await TestBed.configureTestingModule({
      imports: [InstitutionList],
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
          provide: InstitutionService,
          useValue: institutionService,
        },
        {
          provide: InstitutionListQueryStateService,
          useValue: {
            getInitialQuery: () => of({ page: 0, size: 10, first: 0 }),
            saveQuery: vi.fn(),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InstitutionList);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should reload institutions after state change', async () => {
    expect(institutionService.searchInstitutions).toHaveBeenCalledTimes(1);

    component.changeState({
      id: 1,
      name: 'Institution',
      state: InstitutionListViewDto.StateEnum.Active,
    });

    fixture.detectChanges();
    await fixture.whenStable();

    expect(institutionService.changeInstitutionState).toHaveBeenCalledWith(1, {
      state: InstitutionListViewDto.StateEnum.Inactive,
    });
    expect(institutionService.searchInstitutions).toHaveBeenCalledTimes(2);
  });
});
