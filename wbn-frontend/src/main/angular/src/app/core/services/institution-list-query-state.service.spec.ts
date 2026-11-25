import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { InstitutionListQuery, InstitutionService } from '../backend';
import { installSessionStorageMock } from '../../testing/session-storage-testing';
import { InstitutionListQueryStateService } from './institution-list-query-state.service';

const STORAGE_KEY = 'institutionList.query';

describe('InstitutionListQueryStateService', () => {
  let service: InstitutionListQueryStateService;
  let institutionService: {
    getDefaultInstitutionListQuery: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    installSessionStorageMock();
    institutionService = {
      getDefaultInstitutionListQuery: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: InstitutionService, useValue: institutionService }],
    });
    service = TestBed.inject(InstitutionListQueryStateService);
  });

  it('should return query from session storage without backend request', () => {
    const storedQuery: InstitutionListQuery = {
      name: 'University',
      institutionType: InstitutionListQuery.InstitutionTypeEnum.ResearchUnit,
    };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(storedQuery));

    service.getInitialQuery().subscribe((query) => {
      expect(query).toEqual(storedQuery);
    });

    expect(institutionService.getDefaultInstitutionListQuery).not.toHaveBeenCalled();
  });

  it('should fetch default query from generated OpenAPI service and store it in session storage', () => {
    const defaultQuery: InstitutionListQuery = {
      page: 0,
      size: 10,
      first: 0,
    };
    institutionService.getDefaultInstitutionListQuery.mockReturnValue(of(defaultQuery));

    service.getInitialQuery().subscribe((query) => {
      expect(query).toEqual(defaultQuery);
      expect(sessionStorage.getItem(STORAGE_KEY)).toEqual(JSON.stringify(defaultQuery));
    });

    expect(institutionService.getDefaultInstitutionListQuery).toHaveBeenCalledTimes(1);
  });

  it('should remove null values and invalid institution type from stored query', () => {
    const storedQuery = {
      page: null,
      size: 20,
      institutionType: 'UNKNOWN',
    } as unknown as InstitutionListQuery;
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(storedQuery));

    service.getInitialQuery().subscribe((query) => {
      expect(query.page).toBeUndefined();
      expect(query.size).toBe(20);
      expect(query.institutionType).toBeUndefined();
    });

    expect(institutionService.getDefaultInstitutionListQuery).not.toHaveBeenCalled();
  });
});
