import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { UserListQuery, UserService } from '../backend';
import { installSessionStorageMock } from '../../testing/session-storage-testing';
import { UserListQueryStateService } from './user-list-query-state.service';

const GLOBAL_STORAGE_KEY = 'userList.query.global';
const INSTITUTION_STORAGE_KEY = 'userList.query.institution-1';

describe('UserListQueryStateService', () => {
  let service: UserListQueryStateService;
  let userService: {
    getDefaultUserListQuery: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    installSessionStorageMock();
    userService = {
      getDefaultUserListQuery: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: UserService, useValue: userService }],
    });
    service = TestBed.inject(UserListQueryStateService);
  });

  it('should return query from scoped session storage without backend request', () => {
    const storedQuery: UserListQuery = {
      email: 'user@example.org',
      name: 'Jan',
    };
    sessionStorage.setItem(INSTITUTION_STORAGE_KEY, JSON.stringify(storedQuery));

    service.getInitialQuery('institution-1').subscribe((query) => {
      expect(query).toEqual(storedQuery);
    });

    expect(userService.getDefaultUserListQuery).not.toHaveBeenCalled();
  });

  it('should fetch default query from generated OpenAPI service and store it in scoped session storage', () => {
    const defaultQuery: UserListQuery = {
      page: 0,
      size: 10,
      first: 0,
    };
    userService.getDefaultUserListQuery.mockReturnValue(of(defaultQuery));

    service.getInitialQuery().subscribe((query) => {
      expect(query).toEqual(defaultQuery);
      expect(sessionStorage.getItem(GLOBAL_STORAGE_KEY)).toEqual(JSON.stringify(defaultQuery));
    });

    expect(userService.getDefaultUserListQuery).toHaveBeenCalledTimes(1);
  });

  it('should remove null values and invalid institution id from stored query', () => {
    const storedQuery = {
      page: null,
      size: 20,
      institutionId: -1,
    } as unknown as UserListQuery;
    sessionStorage.setItem(GLOBAL_STORAGE_KEY, JSON.stringify(storedQuery));

    service.getInitialQuery().subscribe((query) => {
      expect(query.page).toBeUndefined();
      expect(query.size).toBe(20);
      expect(query.institutionId).toBeUndefined();
    });

    expect(userService.getDefaultUserListQuery).not.toHaveBeenCalled();
  });
});
