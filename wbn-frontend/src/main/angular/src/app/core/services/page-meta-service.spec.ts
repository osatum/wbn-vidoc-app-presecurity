import { ApplicationRef, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of } from 'rxjs';

import { PageMetaService } from './page-meta-service';

@Component({
  standalone: true,
  template: '',
})
class TestPage {}

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({
      'page-meta': {
        home: {
          title: 'Strona glowna',
          description: 'Opis strony',
        },
      },
    } as Translation);
  }
}

describe('PageMetaService', () => {
  beforeEach(() => {
    document.title = '';
    document.querySelector('meta[name="description"]')?.remove();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: '',
            component: TestPage,
            data: {
              title: 'page-meta.home.title',
              description: 'page-meta.home.description',
            },
          },
        ]),
        provideTransloco({
          config: {
            availableLangs: ['pl'],
            defaultLang: 'pl',
            prodMode: true,
          },
          loader: TestingTranslocoLoader,
        }),
      ],
    });
  });

  it('should update document title and description from route data', async () => {
    const service = TestBed.inject(PageMetaService);
    const router = TestBed.inject(Router);

    service.initialize();
    await router.navigateByUrl('/');
    await TestBed.inject(ApplicationRef).whenStable();

    expect(document.title).toBe('Strona glowna');
    expect(document.querySelector('meta[name="description"]')?.getAttribute('content')).toBe('Opis strony');
  });
});
