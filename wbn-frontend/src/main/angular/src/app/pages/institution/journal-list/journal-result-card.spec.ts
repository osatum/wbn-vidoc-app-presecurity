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
import { JournalResultCard } from './journal-result-card';

class TestLoader implements TranslocoLoader {
  getTranslation(language: string) {
    return of(language === 'pl' ? pl : en);
  }
}

const JOURNAL: JournalListView = {
  id: 28032,
  title: 'A journal with a very long title '.repeat(8),
  pissn: '1234-5678',
  eissn: '9876-5432',
  country: { code: 'pl', namePl: 'Polska', nameEn: 'Poland' },
  languages: [
    { code: 'pl', namePl: 'Polski', nameEn: 'Polish' },
    { code: 'en', namePl: 'Angielski', nameEn: 'English' },
    { code: 'de', namePl: 'Niemiecki', nameEn: 'German' },
  ],
  currentIcvYear: 2024,
  currentIcvPoints: 45.67,
  currentStatus: 'INDEXED',
  previousIcvYear: 2023,
  previousIcvPoints: 99.99,
  previousStatus: 'INDEXED',
  lastMniswYear: 2024,
  lastMniswPoints: 70,
  publishers: ['First publisher', 'A second publisher '.repeat(12)],
  publisher: 'Old publisher',
  numberOfPublications: 0,
  percentFullText: 11,
  percentAbstracts: 22,
  percentKeywords: 33,
  percentReferences: 44,
  publicationDays: 0,
  fees: { noFees: true, items: [] },
};

describe('JournalResultCard', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [JournalResultCard],
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
          loader: TestLoader,
        }),
      ],
    }),
  );

  async function render(journal: JournalListView = JOURNAL) {
    const fixture = TestBed.createComponent(JournalResultCard);
    fixture.componentRef.setInput('journal', journal);
    fixture.componentRef.setInput('navigationState', { journalListOrigin: 'origin' });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it.each(['pl', 'en'])(
    'links deposited publications to configured issues in a new tab (%s)',
    async (language) => {
      TestBed.inject(TranslocoService).setActiveLang(language);
      const fixture = await render();
      const root: HTMLElement = fixture.nativeElement;
      const link = root.querySelector<HTMLAnchorElement>(
        '[data-testid="journal-publications-link"]',
      )!;
      expect(link.href).toBe('https://journals.example.test/issues/28032');
      expect(link.target).toBe('_blank');
      expect(link.rel).toBe('noopener noreferrer');
      expect(link.getAttribute('aria-label')).toBe(
        (language === 'pl' ? pl : en).common.depositedPublicationsNewTab,
      );
      expect(link.textContent).toContain(
        (language === 'pl' ? pl : en).page.institution.journalList.depositedPublications,
      );
      expect(root.querySelector('[data-testid="journal-result-deposits"] a span')?.textContent).toBe(
        '0',
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
      fixture.nativeElement.querySelector('[data-testid="journal-result-deposits"]').textContent,
    ).toContain('Zdeponowane publikacje:');
    expect(
      fixture.nativeElement.querySelector('[data-testid="journal-result-deposits"]').textContent,
    ).toMatch(/:\s*0/);
  });

  it('renders the full title, both ISSNs, every publisher, ministry points and both ICV years without legacy percentages', async () => {
    const fixture = await render();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('h3')!.textContent!.trim()).toBe(JOURNAL.title!.trim());
    expect(element.querySelector('article')!.getAttribute('aria-labelledby')).toBe(
      'journal-result-title-28032',
    );
    expect(element.querySelectorAll('.journal-result-ratings .card')).toHaveLength(3);
    expect(
      element.querySelector('.journal-result-metadata')!.classList.contains('description-custom'),
    ).toBe(true);
    expect(element.textContent).toContain('1234-5678');
    expect(element.textContent).toContain('9876-5432');
    expect(element.textContent).toContain(JOURNAL.publishers![1]);
    expect(element.textContent).not.toContain('Old publisher');
    expect(element.querySelector('.journal-result-icv')!.textContent).toContain('ICV 2024:');
    expect(element.querySelector('.journal-result-icv')!.textContent).toContain('45,67');
    expect(element.querySelector('.journal-result-mnisw')!.textContent).toContain('70 pkt');
    expect(
      element.querySelector('[data-testid="journal-result-icv-previous"]')!.textContent,
    ).toContain('ICV 2023:');
    expect(
      element.querySelector('[data-testid="journal-result-icv-previous"]')!.textContent,
    ).toContain('99,99');
    expect(element.textContent).not.toContain('%');
    expect(element.querySelector('input[type="checkbox"]')).toBeNull();
    expect(element.textContent).not.toMatch(
      /Pełna treść|Abstrakt|Słowa kluczowe|Referencje|odrzuceń|porównania/,
    );
    expect(element.querySelector('[data-testid="journal-result-deposits"] a span')!.textContent).toBe(
      '0',
    );
    expect(
      element.querySelector('[data-testid="journal-result-publication-time"]')!.textContent,
    ).toContain('0 dni');
    expect(element.querySelectorAll('[data-testid="journal-result-fees"] p')).toHaveLength(1);
  });

  it.each([
    ['pl', 'INDEXED'],
    ['pl', 'NOT_INDEXED'],
    ['pl', 'NOT_EVALUATED'],
    ['en', 'INDEXED'],
    ['en', 'NOT_INDEXED'],
    ['en', 'NOT_EVALUATED'],
  ] as const)(
    'keeps both ICV badges in one right-aligned equal-width column for %s and previous %s',
    async (language, previousStatus) => {
      TestBed.inject(TranslocoService).setActiveLang(language);
      const fixture = await render({ ...JOURNAL, lastMniswPoints: undefined, previousStatus });
      const root: HTMLElement = fixture.nativeElement;
      const ratings = root.querySelector<HTMLElement>('.journal-result-ratings')!;
      const ministry = ratings.querySelector('.journal-result-mnisw')!;
      const column = ratings.querySelector<HTMLElement>('.journal-result-icv-column')!;
      const current = ratings.querySelector('[data-testid="journal-result-icv-current"]')!;
      const previous = ratings.querySelector('[data-testid="journal-result-icv-previous"]')!;
      expect([...ratings.children]).toEqual([ministry, column]);
      expect([...column.children]).toEqual([current, previous]);
      const layout = getComputedStyle(ratings);
      expect(layout.display).toBe('flex');
      expect(layout.flexWrap).toBe('wrap');
      expect(layout.alignItems).toBe('flex-start');
      expect(layout.justifyContent).toBe('flex-end');
      const icvLayout = getComputedStyle(column);
      expect(icvLayout.display).toBe('grid');
      expect(icvLayout.gridTemplateColumns.replace(/\s/g, '')).toBe('minmax(0,1fr)');
      expect(icvLayout.minWidth).toBe('max-content');
      expect(icvLayout.justifyItems).toBe('stretch');
      expect(icvLayout.gap).toBe('0.5rem');
    },
  );

  it('uses the same heading size and weight utilities as the journal details title', async () => {
    const fixture = await render();
    const element: HTMLElement = fixture.nativeElement;
    const title = element.querySelector('h3.journal-result-title')!;
    for (const className of ['card-title', 'fs-4', 'fw-semibold']) {
      expect(title.classList.contains(className)).toBe(true);
    }
    expect(title.querySelector('a')!.textContent!.trim()).toBe(JOURNAL.title!.trim());
  });

  it.each([
    ['pl', '40 pkt', '97,54'],
    ['en', '40 pts', '97.54'],
  ] as const)(
    'keeps compact rating labels and values on one line in %s',
    async (language, ministry, icv) => {
      TestBed.inject(TranslocoService).setActiveLang(language);
      const fixture = await render({ ...JOURNAL, lastMniswPoints: 40, currentIcvPoints: 97.54 });
      const element: HTMLElement = fixture.nativeElement;
      expect(element.querySelector('.journal-result-mnisw dt')!.textContent!.trim()).toBe('MNiSW:');
      expect(element.querySelector('.journal-result-mnisw dd')!.textContent!.trim()).toBe(ministry);
      expect(element.querySelector('.journal-result-icv dt')!.textContent!.trim()).toBe(
        'ICV 2024:',
      );
      expect(element.querySelector('.journal-result-icv dd')!.textContent!.trim()).toBe(icv);
      for (const rating of Array.from(
        element.querySelectorAll<HTMLElement>('.journal-result-ratings .card'),
      )) {
        expect(getComputedStyle(rating).flexDirection).toBe('row');
        expect(getComputedStyle(rating).flexWrap).toBe('nowrap');
        expect(getComputedStyle(rating.querySelector('dt')!).whiteSpace).toBe('nowrap');
        expect(getComputedStyle(rating.querySelector('dd')!).whiteSpace).toBe('nowrap');
        const isMinistry = rating.classList.contains('journal-result-mnisw');
        expect(rating.classList.contains(isMinistry ? 'bg-info-subtle' : 'bg-success-subtle')).toBe(true);
        expect(rating.classList.contains(isMinistry ? 'text-primary' : 'text-body')).toBe(true);
      }
      expect(element.textContent).not.toMatch(/Publikacja MNiSW|MNiSW publication/);
    },
  );

  it('restores the original icons and eISSN, publisher, country and language arrangement', async () => {
    const fixture = await render({
      ...JOURNAL,
      pissn: undefined,
      eissn: '3023-8277',
      publishers: ['Burhan Erdoğan'],
      country: { code: 'tr', namePl: 'Turcja', nameEn: 'Turkey' },
      languages: [{ code: 'tr', namePl: 'Turecki', nameEn: 'Turkish' }],
    });
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('.journal-result-identifiers dd')!.textContent!.trim()).toBe(
      '3023-8277 (eISSN)',
    );
    expect(
      element.querySelector('[data-testid="journal-result-publishers"]')!.textContent,
    ).toContain('Burhan Erdoğan');
    const location = element.querySelector('.journal-result-location')!;
    expect(location.textContent!.replace(/\s+/g, ' ').trim()).toBe(
      'Kraj: Turcja | Języki: Turecki',
    );
    expect(location.classList.contains('flex-wrap')).toBe(true);
    for (const icon of ['ri-global-line', 'ri-bank-line', 'ri-flag-line', 'ri-translate-2']) {
      expect(element.querySelector('.' + icon)!.getAttribute('aria-hidden')).toBe('true');
    }
  });

  it('keeps a long publisher name in the same text flow as its label without truncation or forcing a separate flex row', async () => {
    const publishers = [
      'Oddział Polskiej Akademii Nauk we Wrocławiu',
      'Kolegium Międzydziedzinowych Studiów Indywidualnych UWr',
    ];
    const fixture = await render({ ...JOURNAL, publishers });
    const element: HTMLElement = fixture.nativeElement;
    const publisher = element.querySelector<HTMLElement>('.journal-result-publisher')!;
    const label = publisher.querySelector('dt')!;
    const value = publisher.querySelector('dd')!;
    expect(publisher.textContent!.replace(/\s+/g, ' ').trim()).toBe(
      `Wydawca: ${publishers.join('; ')}`,
    );
    expect(getComputedStyle(publisher).display).toBe('block');
    expect(getComputedStyle(label).display).toBe('inline');
    expect(getComputedStyle(value).display).toBe('inline');
    expect(getComputedStyle(label).marginInlineEnd).toBe('0.4rem');
    expect(getComputedStyle(value).whiteSpace).not.toBe('nowrap');
    expect(getComputedStyle(value).textOverflow).not.toBe('ellipsis');
    expect(value.querySelector('.ri-bank-line')!.getAttribute('aria-hidden')).toBe('true');
  });

  it.each([
    ['1234-5678', '9876-5432', 1],
    ['1234-5678', undefined, 0],
    [undefined, '9876-5432', 0],
    [undefined, undefined, 0],
  ] as const)(
    'only separates available identifiers: pISSN %s, eISSN %s',
    async (pissn, eissn, separators) => {
      const fixture = await render({ ...JOURNAL, pissn, eissn });
      const element: HTMLElement = fixture.nativeElement;
      expect(
        element.querySelectorAll('.journal-result-identifiers .journal-result-separator'),
      ).toHaveLength(separators);
      expect(element.querySelector('.journal-result-identifiers .ri-book-open-line') !== null).toBe(
        Boolean(pissn),
      );
      expect(element.querySelector('.journal-result-identifiers .ri-global-line') !== null).toBe(
        Boolean(eissn),
      );
      expect(element.querySelector('.journal-result-identifiers') !== null).toBe(
        Boolean(pissn || eissn),
      );
      if (pissn)
        expect(element.querySelector('.journal-result-identifiers')!.textContent).toContain(
          `${pissn} (pISSN)`,
        );
      if (eissn)
        expect(element.querySelector('.journal-result-identifiers')!.textContent).toContain(
          `${eissn} (eISSN)`,
        );
    },
  );

  it.each([
    [true, true, 1],
    [true, false, 0],
    [false, true, 0],
    [false, false, 0],
  ] as const)(
    'only separates available country and languages: %s / %s',
    async (hasCountry, hasLanguages, separators) => {
      const fixture = await render({
        ...JOURNAL,
        country: hasCountry ? JOURNAL.country : undefined,
        languages: hasLanguages ? JOURNAL.languages : [],
      });
      const element: HTMLElement = fixture.nativeElement;
      expect(
        element.querySelectorAll('.journal-result-location .journal-result-separator'),
      ).toHaveLength(separators);
      expect(element.querySelector('.journal-result-location .ri-flag-line') !== null).toBe(
        hasCountry,
      );
      expect(element.querySelector('.journal-result-location .ri-translate-2') !== null).toBe(
        hasLanguages,
      );
      expect(element.querySelector('.journal-result-location') !== null).toBe(
        hasCountry || hasLanguages,
      );
      for (const separator of Array.from(element.querySelectorAll('.journal-result-separator'))) {
        expect(separator.getAttribute('aria-hidden')).toBe('true');
      }
    },
  );

  it('shows every language immediately without a disclosure or abbreviated list', async () => {
    const fixture = await render();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('details')).toBeNull();
    expect(element.querySelector('[data-testid="journal-result-languages"]')!.textContent).toBe(
      'Polski, Angielski, Niemiecki',
    );
    expect(element.textContent).not.toContain('(+1)');
  });

  it('does not add a disclosure for two languages and falls back to dictionary codes without a label', async () => {
    const fixture = await render({
      ...JOURNAL,
      languages: [{ code: 'xx' }, { code: 'yy' }],
      country: { code: 'zz' },
    });
    expect(fixture.nativeElement.querySelector('details')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('xx, yy');
    expect(fixture.nativeElement.textContent).toContain('zz');
  });

  it('updates labels, dictionary values and number formatting in EN without changing source data', async () => {
    const fixture = await render({
      ...JOURNAL,
      fees: {
        noFees: false,
        items: [{ category: 'PUBLICATION', amount: 12345.56, currencyCode: 'EUR' }],
      },
    });
    expect(fixture.nativeElement.textContent).toContain('12\u00a0345,56 EUR');
    TestBed.inject(TranslocoService).setActiveLang('en');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain('Poland');
    expect(element.querySelector('[data-testid="journal-result-languages"]')!.textContent).toBe(
      'Polish, English, German',
    );
    expect(element.textContent).toContain('45.67');
    expect(
      element.querySelector('[data-testid="journal-result-icv-previous"]')!.textContent,
    ).toContain('99.99');
    expect(element.textContent).toContain('12,345.56 EUR');
    expect(element.textContent).toContain(en.page.institution.journalList.depositedPublications);
    expect(element.textContent).toContain('Average publication time');
    expect(
      element.querySelector('[data-testid="journal-result-details"]')!.getAttribute('aria-label'),
    ).toBe(`View card: ${JOURNAL.title}`);
    expect(element.textContent).not.toContain('page.institution.');
  });

  it('shows outside-list without a ministry year and preserves a real zero ministry score', async () => {
    const fixture = await render({ ...JOURNAL, lastMniswYear: 2022, lastMniswPoints: undefined });
    const rating: HTMLElement = fixture.nativeElement.querySelector('.journal-result-mnisw');
    expect(rating.textContent).toContain('Spoza wykazu');
    expect(rating.classList.contains('bg-light')).toBe(true);
    expect(rating.classList.contains('text-dark')).toBe(true);
    expect(rating.classList.contains('bg-info-subtle')).toBe(false);
    expect(rating.classList.contains('text-primary')).toBe(false);
    expect(rating.classList.contains('bg-dark-subtle')).toBe(false);
    expect(rating.classList.contains('bg-success-subtle')).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('2022');
    fixture.componentRef.setInput('journal', { ...JOURNAL, lastMniswPoints: 0 });
    fixture.detectChanges();
    expect(rating.textContent).toContain('0 pkt');
    expect(rating.classList.contains('bg-info-subtle')).toBe(true);
    expect(rating.classList.contains('text-primary')).toBe(true);
    expect(rating.classList.contains('bg-light')).toBe(false);
    expect(rating.classList.contains('text-dark')).toBe(false);
    expect(rating.classList.contains('bg-dark-subtle')).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('Spoza wykazu');
  });

  it.each([
    [JournalListView.CurrentStatusEnum.Indexed, 'bg-success-subtle', 'text-body', '45,67'],
    [JournalListView.CurrentStatusEnum.NotIndexed, 'bg-danger-subtle', 'text-danger', 'N/I'],
    [JournalListView.CurrentStatusEnum.NotEvaluated, 'bg-dark-subtle', 'text-body', 'N/E'],
  ] as const)(
    'restores the original ICV theme and value for %s',
    async (status, background, color, value) => {
      const fixture = await render({ ...JOURNAL, currentStatus: status });
      const rating: HTMLElement = fixture.nativeElement.querySelector('.journal-result-icv');
      expect(rating.classList.contains(background)).toBe(true);
      expect(rating.classList.contains(color)).toBe(true);
      expect(rating.querySelector('dt')!.textContent).toBe('ICV 2024:');
      expect(rating.querySelector('dd')!.textContent!.trim()).toBe(value);
      for (const other of ['bg-success-subtle', 'bg-danger-subtle', 'bg-dark-subtle']) {
        expect(rating.classList.contains(other)).toBe(other === background);
      }
    },
  );

  it('preserves a real zero ICV score and applies the ministry color scheme independently', async () => {
    const fixture = await render({ ...JOURNAL, currentIcvPoints: 0 });
    for (const [selector, background, color] of [
      ['.journal-result-icv', 'bg-success-subtle', 'text-body'],
      ['.journal-result-mnisw', 'bg-info-subtle', 'text-primary'],
    ]) {
      const rating: HTMLElement = fixture.nativeElement.querySelector(selector);
      expect(rating.classList.contains('card')).toBe(true);
      expect(rating.classList.contains(background)).toBe(true);
      expect(rating.classList.contains(color)).toBe(true);
    }
    expect(fixture.nativeElement.querySelector('.journal-result-icv dd').textContent.trim()).toBe(
      '0,00',
    );
  });

  it.each([
    [JournalListView.CurrentStatusEnum.NotIndexed, 'N/I', 'notIndexedTooltip'],
    [JournalListView.CurrentStatusEnum.NotEvaluated, 'N/E', 'notEvaluatedTooltip'],
  ] as const)(
    'explains %s with a keyboard-accessible, translated tooltip',
    async (status, value, key) => {
      const fixture = await render({
        ...JOURNAL,
        currentStatus: status,
        currentIcvPoints: undefined,
      });
      const abbreviation: HTMLElement = fixture.nativeElement.querySelector(
        '.journal-result-icv abbr',
      );
      const tooltip = fixture.debugElement.query(By.directive(NgbTooltip)).injector.get(NgbTooltip);
      expect(abbreviation.textContent).toBe(value);
      expect(abbreviation.tabIndex).toBe(0);
      expect(abbreviation.getAttribute('aria-label')).toBe(pl.page.institution.journalList[key]);
      expect(tooltip.triggers).toBe('hover focus');
      expect(tooltip.ngbTooltip).toBe(pl.page.institution.journalList[key]);
      TestBed.inject(TranslocoService).setActiveLang('en');
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(abbreviation.getAttribute('aria-label')).toBe(en.page.institution.journalList[key]);
      expect(tooltip.ngbTooltip).toBe(en.page.institution.journalList[key]);
    },
  );

  it('does not infer indexing from points when the current status is missing', async () => {
    const fixture = await render({ ...JOURNAL, currentStatus: undefined });
    const rating: HTMLElement = fixture.nativeElement.querySelector('.journal-result-icv');
    expect(rating.classList.contains('bg-dark-subtle')).toBe(true);
    expect(rating.querySelector('dd')!.textContent!.trim()).toBe('N/E');
    expect(rating.textContent).not.toContain('45,67');
  });

  it('never substitutes historical ICV for a missing current score', async () => {
    const fixture = await render({
      ...JOURNAL,
      currentIcvYear: undefined,
      currentIcvPoints: undefined,
    });
    fixture.componentRef.setInput('icvYear', 2024);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.journal-result-icv').textContent).toContain(
      'ICV 2024:',
    );
    expect(fixture.nativeElement.querySelector('.journal-result-icv dd').textContent.trim()).toBe(
      '–',
    );
    expect(
      fixture.nativeElement.querySelector('[data-testid="journal-result-icv-current"]').textContent,
    ).not.toContain('99,99');
    expect(
      fixture.nativeElement.querySelector('[data-testid="journal-result-icv-previous"]')
        .textContent,
    ).toContain('99,99');
  });

  it.each([
    ['INDEXED', 'bg-success-subtle', 'text-body', '99,99'],
    ['NOT_INDEXED', 'bg-danger-subtle', 'text-danger', 'N/I'],
    ['NOT_EVALUATED', 'bg-dark-subtle', 'text-body', 'N/E'],
  ] as const)(
    'shows an independent previous-year %s rating with the same theme as current ICV',
    async (status, background, color, value) => {
      const fixture = await render({
        ...JOURNAL,
        currentStatus: 'NOT_EVALUATED',
        previousStatus: status,
      });
      const root: HTMLElement = fixture.nativeElement;
      const previous = root.querySelector('[data-testid="journal-result-icv-previous"]')!;
      expect(previous.querySelector('dt')!.textContent).toBe('ICV 2023:');
      expect(previous.querySelector('dd')!.textContent!.trim()).toBe(value);
      expect(previous.classList.contains(background)).toBe(true);
      expect(previous.classList.contains(color)).toBe(true);
      for (const other of ['bg-success-subtle', 'bg-danger-subtle', 'bg-dark-subtle'])
        expect(previous.classList.contains(other)).toBe(other === background);
      expect(
        root.querySelector('[data-testid="journal-result-icv-current"] dd')!.textContent!.trim(),
      ).toBe('N/E');
    },
  );

  it.each([
    ['NOT_INDEXED', 'N/I', 'notIndexedTooltip'],
    ['NOT_EVALUATED', 'N/E', 'notEvaluatedTooltip'],
  ] as const)(
    'explains previous-year %s with a keyboard-accessible translated tooltip',
    async (status, abbreviation, key) => {
      const fixture = await render({ ...JOURNAL, previousStatus: status });
      const previous = fixture.debugElement.query(
        By.css('[data-testid="journal-result-icv-previous"]'),
      );
      const element: HTMLElement = previous.nativeElement.querySelector('abbr');
      const tooltip = previous.query(By.directive(NgbTooltip)).injector.get(NgbTooltip);
      expect(element.textContent).toBe(abbreviation);
      expect(element.tabIndex).toBe(0);
      expect(element.getAttribute('aria-label')).toBe(pl.page.institution.journalList[key]);
      expect(tooltip.ngbTooltip).toBe(pl.page.institution.journalList[key]);
      expect(tooltip.triggers).toBe('hover focus');
      TestBed.inject(TranslocoService).setActiveLang('en');
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(element.getAttribute('aria-label')).toBe(en.page.institution.journalList[key]);
      expect(tooltip.ngbTooltip).toBe(en.page.institution.journalList[key]);
    },
  );

  it('preserves a zero previous score without substituting the current score for missing previous points', async () => {
    const fixture = await render({ ...JOURNAL, previousIcvPoints: 0 });
    const previous: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="journal-result-icv-previous"]',
    );
    expect(previous.querySelector('dd')!.textContent!.trim()).toBe('0,00');
    fixture.componentRef.setInput('journal', { ...JOURNAL, previousIcvPoints: undefined });
    fixture.detectChanges();
    expect(previous.querySelector('dd')!.textContent!.trim()).toBe('–');
    expect(previous.textContent).not.toContain('45,67');
  });

  it('uses configured years only for missing response years and does not invent a previous status from points', async () => {
    const fixture = await render({
      ...JOURNAL,
      previousIcvYear: undefined,
      previousStatus: undefined,
    });
    fixture.componentRef.setInput('icvYear', 2030);
    fixture.componentRef.setInput('previousIcvYear', 2022);
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="journal-result-icv-current"]').textContent,
    ).toContain('ICV 2024:');
    const previous: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="journal-result-icv-previous"]',
    );
    expect(previous.querySelector('dt')!.textContent).toBe('ICV 2022:');
    expect(previous.querySelector('dd')!.textContent!.trim()).toBe('N/E');
    expect(previous.classList.contains('bg-dark-subtle')).toBe(true);
    expect(previous.textContent).not.toContain('99,99');
    fixture.componentRef.setInput('journal', JOURNAL);
    fixture.detectChanges();
    expect(previous.querySelector('dt')!.textContent).toBe('ICV 2023:');
  });

  it('shows placeholders when neither data nor configuration provide rating years', async () => {
    const fixture = await render({ id: 1 });
    const ratings: NodeListOf<HTMLElement> =
      fixture.nativeElement.querySelectorAll('.journal-result-icv');
    expect(ratings).toHaveLength(2);
    for (const rating of ratings) {
      expect(rating.querySelector('dt')!.textContent).toBe('ICV –:');
      expect(rating.querySelector('dd')!.textContent!.trim()).toBe('N/E');
    }
  });

  it('reserves cover dimensions for missing and failed covers without adding another tab stop', async () => {
    const fixture = await render();
    const image = fixture.debugElement.query(By.directive(SafeImageComponent))
      .componentInstance as SafeImageComponent;
    expect(image.state()).toBe('error');
    expect(image.width()).toBe(120);
    expect(image.height()).toBe(176);
    expect(image.objectFit()).toBe('contain');
    expect(fixture.nativeElement.querySelector('.journal-result-cover a')).toBeNull();
    fixture.componentRef.setInput('journal', { ...JOURNAL, cover: '/test-cover.jpg' });
    fixture.detectChanges();
    image.onImageError();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.safe-image__fallback')).toBeTruthy();
    expect(image.aspectRatio()).toBe('120 / 176');
  });

  it.each([
    ['pl', 'Zobacz kartę'],
    ['en', 'View journal'],
  ])(
    'uses the original underlined details link style while keeping the view-card label in %s',
    async (language, label) => {
      TestBed.inject(TranslocoService).setActiveLang(language);
      const fixture = await render();
      const element: HTMLElement = fixture.nativeElement;
      const link = element.querySelector<HTMLAnchorElement>(
        '[data-testid="journal-result-details"]',
      )!;
      expect(link.tagName).toBe('A');
      expect(link.classList.contains('text-decoration-underline')).toBe(true);
      expect(link.classList.contains('btn')).toBe(false);
      expect(link.classList.contains('btn-outline-primary')).toBe(false);
      expect(link.getAttribute('role')).toBeNull();
      expect(link.textContent!.trim()).toBe(label);
      expect(link.getAttribute('aria-label')).toContain(JOURNAL.title!.trim());
      expect(link.getAttribute('href')).toBe('/institution/user/journals/28032');
      expect(link.querySelector('.ri-file-text-line')).toBeNull();
      expect(link.lastElementChild!.classList.contains('ri-arrow-right-line')).toBe(true);
      expect(link.lastElementChild!.getAttribute('aria-hidden')).toBe('true');
      expect(getComputedStyle(link).justifySelf).toBe('start');
    },
  );

  it('passes the selected year in state on both links without changing their public URL', async () => {
    const fixture = await render();
    fixture.componentRef.setInput('surveyYear', 2024);
    fixture.detectChanges();
    for (const link of fixture.debugElement.queryAll(By.directive(RouterLink))) {
      expect(link.nativeElement.getAttribute('href')).toBe('/institution/user/journals/28032');
      expect(link.injector.get(RouterLink).state).toMatchObject({ surveyYear: 2024 });
    }
  });

  it('keeps both links in the current tab with the same public id and forwards modified clicks unchanged', async () => {
    const fixture = await render();
    const clicks = vi.fn();
    fixture.componentInstance.detailsClick.subscribe(clicks);
    const links = fixture.debugElement
      .queryAll(By.directive(RouterLink))
      .map((element) => element.nativeElement as HTMLAnchorElement);
    expect(links).toHaveLength(2);
    for (const link of links) {
      expect(link.getAttribute('href')).toBe('/institution/user/journals/28032');
      expect(link.getAttribute('target')).toBeNull();
      expect(link.getAttribute('tabindex')).toBeNull();
      const event = new MouseEvent('click', {
        button: 0,
        ctrlKey: true,
        bubbles: true,
        cancelable: true,
      });
      link.dispatchEvent(event);
      expect(clicks).toHaveBeenLastCalledWith(event);
      expect(event.defaultPrevented).toBe(false);
    }
  });
});
