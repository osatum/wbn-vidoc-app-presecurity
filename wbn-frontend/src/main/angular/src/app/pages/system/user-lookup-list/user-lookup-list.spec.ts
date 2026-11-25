import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of } from 'rxjs';
import { UserListViewDto, UserService } from '../../../core/backend';
import { UserLookupList } from './user-lookup-list';
import { UserListQueryStateService } from '../../../core/services/user-list-query-state.service';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('UserLookupList', () => {
  let component: UserLookupList;
  let fixture: ComponentFixture<UserLookupList>;
  let userService: {
    findUsers: ReturnType<typeof vi.fn>;
    findUsersByInstitution: ReturnType<typeof vi.fn>;
    changeUserState: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    userService = {
      findUsers: vi.fn(() => of({ data: [], totalElements: 0, totalPages: 0 })),
      findUsersByInstitution: vi.fn(() => of({ data: [], totalElements: 0, totalPages: 0 })),
      changeUserState: vi.fn(() => of(undefined)),
    };

    await TestBed.configureTestingModule({
      imports: [UserLookupList],
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
          provide: UserService,
          useValue: userService,
        },
        {
          provide: UserListQueryStateService,
          useValue: {
            getInitialQuery: () => of({ page: 0, size: 10, first: 0 }),
            saveQuery: vi.fn(),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UserLookupList);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should reload users after state change', async () => {
    expect(userService.findUsers).toHaveBeenCalledTimes(1);

    component.changeState({
      id: 1,
      email: 'user@example.com',
      state: UserListViewDto.StateEnum.Active,
    });

    fixture.detectChanges();
    await fixture.whenStable();

    expect(userService.changeUserState).toHaveBeenCalledWith(1, { state: UserListViewDto.StateEnum.Inactive });
    expect(userService.findUsers).toHaveBeenCalledTimes(2);
  });
});
