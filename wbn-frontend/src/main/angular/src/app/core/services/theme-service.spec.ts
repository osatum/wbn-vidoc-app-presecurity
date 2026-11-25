import { TestBed } from '@angular/core/testing';

import { installLocalStorageMock } from '../../testing/local-storage-testing';
import { ThemeService } from './theme-service';

describe('ThemeService', () => {
  let service: ThemeService;

  beforeEach(() => {
    installLocalStorageMock();
    TestBed.configureTestingModule({});
    service = TestBed.inject(ThemeService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
