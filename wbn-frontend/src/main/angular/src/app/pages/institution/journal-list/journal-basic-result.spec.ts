import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { RouterLink, provideRouter } from '@angular/router';
import { provideTransloco, TranslocoLoader, TranslocoService } from '@jsverse/transloco';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { of, throwError } from 'rxjs';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';
import { ConfigurationService, JournalListView } from '../../../core/backend';
import { SafeImageComponent } from '../../../shared/safe-image/safe-image.component';
import { JournalBasicResult } from './journal-basic-result';

class Loader implements TranslocoLoader {
  getTranslation(language: string) {
    return of(language === 'pl' ? pl : en);
  }
}

const JOURNAL: JournalListView = {
  id: 123,
  title: 'Original journal title',
  pissn: '1234-5678',
  eissn: '9876-5432',
  publisher: 'Original publisher',
  country: { code: 'pl', namePl: 'Polska', nameEn: 'Poland' },
  languages: [{ code: 'en', namePl: 'Angielski', nameEn: 'English' }],
  currentIcvYear: 2024,
  currentIcvPoints: 45.67,
  currentStatus: 'INDEXED',
  previousIcvYear: 2023,
  previousIcvPoints: 99.99,
  previousStatus: 'INDEXED',
  lastMniswPoints: 70,
  numberOfPublications: 10,
  percentFullText: 11,
  percentAbstracts: 22,
  percentKeywords: 33,
  percentReferences: 44,
  publicationDays: 5,
  fees: { noFees: true, items: [] },
};

describe('JournalBasicResult original layout', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [JournalBasicResult],
      providers: [
        provideRouter([]),
        {
          provide: ConfigurationService,
          useValue: {
            getJournalLinks: vi.fn(() =>
              of({ issuesUrlTemplate: 'https://journals.example.test/issues/{journalId}' }),
            ),
          },
        },
        provideTransloco({
          config: {
            availableLangs: ['pl', 'en'],
            defaultLang: 'pl',
            reRenderOnLangChange: true,
            prodMode: true,
          },
          loader: Loader,
        }),
      ],
    }),
  );

  async function render(journal: JournalListView = JOURNAL) {
    const fixture = TestBed.createComponent(JournalBasicResult);
    fixture.componentRef.setInput('journal', journal);
    fixture.componentRef.setInput('navigationState', { journalListOrigin: 'basic-origin' });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('restores the original cover, metadata, both ICV years and deposit percentages instead of advanced cards', async () => {
    const fixture = await render();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('article')!.classList.contains('flex-sm-row')).toBe(true);
    expect(root.querySelector('article')!.getAttribute('aria-labelledby')).toBe(
      'basic-journal-title-123',
    );
    expect(root.querySelector('.col-xxl-10 h2')!.textContent!.trim()).toBe(JOURNAL.title);
    for (const text of [
      '1234-5678 (pISSN)',
      '9876-5432 (eISSN)',
      'Original publisher',
      'Polska',
      'Angielski',
      '70',
    ]) {
      expect(root.textContent).toContain(text);
    }
    const ratings = root.querySelectorAll('.col-xxl-2 [data-testid="journal-basic-icv"]');
    expect(ratings).toHaveLength(2);
    expect(ratings[0].textContent).toContain('ICV 2024:');
    expect(ratings[0].textContent).toContain('45,67');
    expect(ratings[1].textContent).toContain('ICV 2023:');
    expect(ratings[1].textContent).toContain('99,99');
    const deposits = root.querySelector('[data-testid="journal-basic-deposit"]')!.textContent;
    for (const text of ['10', '11%', '22%', '33%', '44%']) expect(deposits).toContain(text);
    expect(root.querySelector('[data-testid="journal-result-publication-time"]')).toBeNull();
    expect(root.querySelector('[data-testid="journal-result-fees"]')).toBeNull();
    expect(root.querySelector('input[type="checkbox"]')).toBeNull();
    const image = fixture.debugElement.query(By.directive(SafeImageComponent))
      .componentInstance as SafeImageComponent;
    expect(image.width()).toBe(240);
    expect(image.height()).toBe(300);
    expect(image.objectFit()).toBe('fill');
    expect(image.alt()).toBe('');
  });

  it.each(['pl', 'en'])(
    'links deposited publications to configured issues in a new tab (%s)',
    async (language) => {
      TestBed.inject(TranslocoService).setActiveLang(language);
      const fixture = await render({ ...JOURNAL, numberOfPublications: 0 });
      const root: HTMLElement = fixture.nativeElement;
      const link = root.querySelector<HTMLAnchorElement>(
        '[data-testid="journal-publications-link"]',
      )!;
      expect(link.href).toBe('https://journals.example.test/issues/123');
      expect(link.target).toBe('_blank');
      expect(link.rel).toBe('noopener noreferrer');
      expect(link.getAttribute('aria-label')).toBe(
        (language === 'pl' ? pl : en).common.depositedPublicationsNewTab,
      );
      expect(link.textContent).toContain(
        (language === 'pl' ? pl : en).page.institution.journalList.depositedPublications,
      );
      expect(root.querySelector('[data-testid="journal-basic-deposit"]')?.textContent).toMatch(
        /:\s*0/,
      );
      expect(link.textContent).toMatch(/:\s*0/);
      const issuesClick = vi.fn();
      fixture.componentInstance.issuesClick.subscribe(issuesClick);
      const detailsClick = vi.fn();
      fixture.componentInstance.detailsClick.subscribe(detailsClick);
      link.dispatchEvent(new MouseEvent('click', { ctrlKey: true, bubbles: true }));
      link.dispatchEvent(new MouseEvent('auxclick', { button: 1, bubbles: true }));
      expect(detailsClick).not.toHaveBeenCalled();
      expect(issuesClick).toHaveBeenCalledTimes(2);
      fixture.componentRef.setInput('journal', { ...JOURNAL, id: 456 });
      fixture.detectChanges();
      expect(link.href).toBe('https://journals.example.test/issues/456');
      expect(TestBed.inject(ConfigurationService).getJournalLinks).toHaveBeenCalledTimes(1);
    },
  );

  it('keeps the publication label and count as text when link configuration fails', async () => {
    vi.mocked(TestBed.inject(ConfigurationService).getJournalLinks).mockReturnValue(
      throwError(() => new Error('offline')),
    );
    const fixture = await render();
    expect(
      fixture.nativeElement.querySelector('[data-testid="journal-publications-link"]'),
    ).toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="journal-basic-deposit"]').textContent,
    ).toContain('Zdeponowane publikacje:');
    expect(
      fixture.nativeElement.querySelector('[data-testid="journal-basic-deposit"]').textContent,
    ).toContain('10');
  });

  it('renders previous-year points independently from missing current-year points', async () => {
    const fixture = await render({ ...JOURNAL, currentIcvPoints: undefined });
    const ratings = fixture.nativeElement.querySelectorAll('[data-testid="journal-basic-icv"]');
    expect(ratings[0].textContent.replace(/\s+/g, ' ').trim()).toBe('ICV 2024: -');
    expect(ratings[1].textContent).toContain('99,99');
  });

  it('preserves zero ICV, ministry points, deposits and percentages', async () => {
    const fixture = await render({
      ...JOURNAL,
      currentIcvPoints: 0,
      previousIcvPoints: 0,
      lastMniswPoints: 0,
      numberOfPublications: 0,
      percentFullText: 0,
      percentAbstracts: 0,
      percentKeywords: 0,
      percentReferences: 0,
    });
    const root: HTMLElement = fixture.nativeElement;
    for (const rating of root.querySelectorAll('[data-testid="journal-basic-icv"]'))
      expect(rating.textContent).toContain('0,00');
    expect(root.textContent).not.toContain(pl.page.institution.journalList.outsideMniswList);
    const deposits = root.querySelector('[data-testid="journal-basic-deposit"]')!.textContent!;
    expect(deposits.match(/0%/g)).toHaveLength(4);
    expect(deposits).toContain(': 0');
  });

  it('uses placeholders rather than false zero statistics for unavailable data', async () => {
    const fixture = await render({ id: 123, title: 'Missing metadata' });
    const root: HTMLElement = fixture.nativeElement;
    expect(root.textContent).toContain(pl.page.institution.journalList.outsideMniswList);
    const deposits = root.querySelector('[data-testid="journal-basic-deposit"]')!.textContent!;
    expect(deposits).not.toContain('%');
    expect(deposits.match(/-/g)).toHaveLength(5);
    for (const rating of root.querySelectorAll('[data-testid="journal-basic-icv"]')) {
      expect(rating.textContent).toContain('ICV -:');
      expect(rating.textContent).toContain('N/E');
    }
  });

  it('uses configured year labels only when the corresponding response year is missing', async () => {
    const fixture = await render({ ...JOURNAL, previousIcvYear: undefined });
    fixture.componentRef.setInput('icvYear', 2030);
    fixture.componentRef.setInput('previousIcvYear', 2022);
    fixture.detectChanges();
    const ratings = fixture.nativeElement.querySelectorAll('[data-testid="journal-basic-icv"]');
    expect(ratings[0].textContent).toContain('ICV 2024:');
    expect(ratings[1].textContent).toContain('ICV 2022:');
  });

  it.each([
    ['INDEXED', 'bg-success-subtle', '45,67', '99,99'],
    ['NOT_INDEXED', 'bg-danger-subtle', 'N/I', 'N/I'],
    ['NOT_EVALUATED', 'bg-dark-subtle', 'N/E', 'N/E'],
  ] as const)(
    'restores the %s presentation for both years',
    async (status, background, current, previous) => {
      const fixture = await render({ ...JOURNAL, currentStatus: status, previousStatus: status });
      const ratings = fixture.nativeElement.querySelectorAll(
        '[data-testid="journal-basic-icv"]',
      ) as NodeListOf<HTMLElement>;
      for (const [index, rating] of [...ratings].entries()) {
        expect(rating.classList.contains(background)).toBe(true);
        expect(rating.textContent).toContain(index === 0 ? current : previous);
        for (const other of ['bg-success-subtle', 'bg-danger-subtle', 'bg-dark-subtle'])
          expect(rating.classList.contains(other)).toBe(other === background);
      }
    },
  );

  it('keeps distinct keyboard-accessible N/I and N/E explanations for the corresponding years', async () => {
    const fixture = await render({
      ...JOURNAL,
      currentStatus: 'NOT_INDEXED',
      previousStatus: 'NOT_EVALUATED',
    });
    const tooltips = fixture.debugElement.queryAll(By.directive(NgbTooltip));
    expect(tooltips).toHaveLength(2);
    for (const [index, element] of tooltips.entries()) {
      const key = index === 0 ? 'notIndexedTooltip' : 'notEvaluatedTooltip';
      const tooltip = element.injector.get(NgbTooltip);
      expect(element.nativeElement.tabIndex).toBe(0);
      expect(element.nativeElement.getAttribute('aria-label')).toBe(
        pl.page.institution.journalList[key],
      );
      expect(tooltip.ngbTooltip).toBe(pl.page.institution.journalList[key]);
      expect(tooltip.triggers).toBe('hover focus');
    }
  });

  it('updates country, language, points and explanations after switching language', async () => {
    const fixture = await render({ ...JOURNAL, previousStatus: 'NOT_EVALUATED' });
    TestBed.inject(TranslocoService).setActiveLang('en');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Poland');
    expect(fixture.nativeElement.textContent).toContain('English');
    expect(fixture.nativeElement.textContent).toContain('45.67');
    expect(
      fixture.debugElement.query(By.directive(NgbTooltip)).injector.get(NgbTooltip).ngbTooltip,
    ).toBe(en.page.institution.journalList.notEvaluatedTooltip);
  });

  it('passes the selected year through Angular state instead of adding it to the public URL', async () => {
    const fixture = await render();
    fixture.componentRef.setInput('surveyYear', 2024);
    fixture.detectChanges();
    for (const link of fixture.debugElement.queryAll(By.directive(RouterLink))) {
      expect(link.nativeElement.getAttribute('href')).toBe('/institution/user/journals/123');
      expect(link.injector.get(RouterLink).state).toEqual({
        journalListOrigin: 'basic-origin',
        surveyYear: 2024,
      });
    }
  });

  it('retains the same details URLs and return state on both original links', async () => {
    const fixture = await render();
    const links = fixture.debugElement.queryAll(By.directive(RouterLink));
    expect(links).toHaveLength(2);
    for (const link of links) {
      expect(link.nativeElement.getAttribute('href')).toBe('/institution/user/journals/123');
      expect(link.injector.get(RouterLink).state).toEqual({ journalListOrigin: 'basic-origin' });
      expect(link.nativeElement.getAttribute('target')).toBeNull();
    }
    expect(links[1].nativeElement.classList.contains('text-decoration-underline')).toBe(true);
    expect(links[1].nativeElement.textContent.trim()).toBe(pl.common.details);
    const clicked = vi.fn();
    fixture.componentInstance.detailsClick.subscribe(clicked);
    const event = new MouseEvent('auxclick', { button: 1, bubbles: true, cancelable: true });
    links[0].nativeElement.dispatchEvent(event);
    expect(clicked).toHaveBeenCalledWith(event);
    expect(event.defaultPrevented).toBe(false);
  });
});
