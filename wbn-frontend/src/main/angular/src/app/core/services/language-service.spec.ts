import { TestBed } from '@angular/core/testing';

import { translocoTestingModule } from '../../testing/transloco-testing-module';
import { LanguageService } from './language-service';

describe('LanguageService', () => {
  let service: LanguageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [translocoTestingModule],
    });
    service = TestBed.inject(LanguageService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
