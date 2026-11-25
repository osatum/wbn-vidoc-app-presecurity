import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of } from 'rxjs';
import { UserListQuery, UserService } from '../../../core/backend';

import { UserList } from './user-list';
import { UserListQueryStateService } from '../../../core/services/user-list-query-state.service';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('UserList', () => {
  let component: UserList;
  let fixture: ComponentFixture<UserList>;
  let userService: {
    findUsers: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    userService = {
      findUsers: vi.fn(() => of({ data: [], totalElements: 0, totalPages: 0 })),
    };

    await TestBed.configureTestingModule({
      imports: [UserList],
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

    fixture = TestBed.createComponent(UserList);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should send sorting selected in the select field', async () => {
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#user-sort-by');

    expect(select.options).toHaveLength(4);

    select.value = UserListQuery.SortByEnum.SortByEmailDesc;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.sortBy()).toBe(UserListQuery.SortByEnum.SortByEmailDesc);
    expect(userService.findUsers).toHaveBeenLastCalledWith(
      expect.objectContaining({ sortBy: UserListQuery.SortByEnum.SortByEmailDesc }),
    );
  });
});
