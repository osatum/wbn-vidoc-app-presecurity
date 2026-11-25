import { TestBed } from '@angular/core/testing';
import {
  provideTransloco,
  TranslocoLoader,
  TranslocoService,
  Translation,
} from '@jsverse/transloco';
import { of } from 'rxjs';
import { JournalAppliedFilters } from './journal-applied-filters';
import { JournalFilterChip } from './journal-filter-criteria';

const TRANSLATIONS: Record<string, Translation> = {
  pl: {
    page: {
      institution: {
        journalList: {
          country: 'Kraj',
          clearFilters: 'Wyczyść filtry',
          publicationDays: 'Czas publikacji',
          feeLabels: { PUBLICATION: 'Publication fee' },
          chips: {
            title: 'Zastosowane filtry',
            remove: 'Usuń filtr {{filter}} i odśwież wyniki',
            pendingChanges: 'Zmieniono kryteria.',
            noFee: 'Brak opłaty',
            range: '{{minimum}}–{{maximum}}',
            minimum: '≥ {{minimum}}',
            maximum: '≤ {{maximum}}',
            units: { days: 'dni', pln: 'PLN', percent: '%' },
          },
        },
      },
    },
  },
  en: {
    page: {
      institution: {
        journalList: {
          country: 'Country',
          clearFilters: 'Clear filters',
          publicationDays: 'Publication time',
          feeLabels: { PUBLICATION: 'Publication fee' },
          chips: {
            title: 'Applied filters',
            remove: 'Remove filter {{filter}} and refresh results',
            pendingChanges: 'Criteria changed.',
            noFee: 'No fee',
            range: '{{minimum}}–{{maximum}}',
            minimum: '≥ {{minimum}}',
            maximum: '≤ {{maximum}}',
            units: { days: 'days', pln: 'PLN', percent: '%' },
          },
        },
      },
    },
  },
};
class TestLoader implements TranslocoLoader {
  getTranslation(language: string) {
    return of(TRANSLATIONS[language] ?? {});
  }
}
const COUNTRY: JournalFilterChip = {
  id: 'countryCodes:pl',
  labelKey: 'page.institution.journalList.country',
  value: 'pl',
  selection: 'countryCodes',
  removal: { kind: 'selection', field: 'countryCodes', code: 'pl' },
};

describe('JournalAppliedFilters', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        provideTransloco({
          config: { availableLangs: ['pl', 'en'], defaultLang: 'pl', prodMode: true },
          loader: TestLoader,
        }),
      ],
    }),
  );

  async function create(chips: JournalFilterChip[] = [COUNTRY]) {
    const fixture = TestBed.createComponent(JournalAppliedFilters);
    fixture.componentRef.setInput('chips', chips);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('emits the clear-all action without removing individual chips or submitting a form', async () => {
    const fixture = await create();
    const clear = vi.fn();
    const remove = vi.fn();
    fixture.componentInstance.clear.subscribe(clear);
    fixture.componentInstance.remove.subscribe(remove);
    const button = fixture.nativeElement.querySelector('button.btn-link') as HTMLButtonElement;
    expect(button.type).toBe('button');
    expect(button.textContent!.trim()).toBe('Wyczyść filtry');
    button.click();
    fixture.detectChanges();
    expect(clear).toHaveBeenCalledExactlyOnceWith(undefined);
    expect(remove).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelectorAll('li')).toHaveLength(1);
  });

  it('keeps applied chips visible after a removal event and announces pending changes exactly', async () => {
    const fixture = await create();
    fixture.componentRef.setInput('labels', { countryCodes: { pl: 'Polska' } });
    fixture.componentRef.setInput('pendingChanges', true);
    fixture.detectChanges();
    const changed = vi.fn();
    fixture.componentInstance.remove.subscribe(changed);
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.type).toBe('button');
    expect(button.getAttribute('aria-label')).toContain('Kraj: Polska');
    button.click();
    fixture.detectChanges();
    expect(changed).toHaveBeenCalledExactlyOnceWith(COUNTRY);
    expect(fixture.nativeElement.querySelectorAll('li')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('[role=status] > span').textContent.trim()).toBe(
      'Zmieniono kryteria.',
    );
  });

  it('uses dictionary codes as a safe fallback and resolves late labels without removal', async () => {
    const fixture = await create();
    const changed = vi.fn();
    fixture.componentInstance.remove.subscribe(changed);
    expect(fixture.nativeElement.textContent).toContain('Kraj: pl');
    fixture.componentRef.setInput('labels', { countryCodes: { pl: 'Polska' } });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Kraj: Polska');
    expect(changed).not.toHaveBeenCalled();
  });

  it('renders inclusive zero, original PLN limits and no-fee flags without omitting zero', async () => {
    const fixture = await create([
      {
        id: 'days',
        labelKey: 'page.institution.journalList.publicationDays',
        maximum: '0',
        unit: 'days',
        removal: { kind: 'range', fields: ['publicationDaysTo'] },
      },
      {
        id: 'amount',
        labelKey: 'page.institution.journalList.feeLabels.PUBLICATION',
        maximum: '123.45',
        unit: 'pln',
        removal: { kind: 'fee', flag: 'publicationFeeFreeOnly', maximum: 'publicationFeeMaxPln' },
      },
      {
        id: 'free',
        labelKey: 'page.institution.journalList.feeLabels.PUBLICATION',
        noFee: true,
        removal: { kind: 'fee', flag: 'publicationFeeFreeOnly' },
      },
    ]);
    expect(fixture.nativeElement.textContent).toContain('Czas publikacji: ≤ 0 dni');
    expect(fixture.nativeElement.textContent).toContain('123,45 PLN');
    expect(fixture.nativeElement.textContent).toContain('Brak opłaty');
    TestBed.inject(TranslocoService).setActiveLang('en');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Publication time: ≤ 0 days');
    expect(fixture.nativeElement.textContent).toContain('123.45 PLN');
    expect(fixture.nativeElement.textContent).toContain('No fee');
  });

  it('supports closed and one-sided ranges without HTML injection from a text criterion', async () => {
    const fixture = await create([
      {
        id: 'range',
        labelKey: 'page.institution.journalList.publicationDays',
        minimum: '1',
        maximum: '10',
        removal: { kind: 'range', fields: ['publicationDaysTo'] },
      },
      {
        id: 'minimum',
        labelKey: 'page.institution.journalList.publicationDays',
        minimum: '0',
        removal: { kind: 'range', fields: ['publicationDaysTo'] },
      },
      {
        id: 'text',
        labelKey: 'page.institution.journalList.country',
        value: '<img src=x onerror=alert(1)>',
        removal: { kind: 'text', field: 'journalTitle' },
      },
    ]);
    expect(fixture.nativeElement.textContent).toContain('1–10');
    expect(fixture.nativeElement.textContent).toContain('≥ 0');
    expect(fixture.nativeElement.textContent).toContain('<img src=x onerror=alert(1)>');
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
  });

  it('hides an empty applied bar but shows the announcement for a new draft and clears it afterwards', async () => {
    const fixture = await create([]);
    expect(fixture.nativeElement.querySelector('section')).toBeNull();
    fixture.componentRef.setInput('pendingChanges', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=status]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('ul')).toBeNull();
    fixture.componentRef.setInput('pendingChanges', false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('section')).toBeNull();
  });

  it.each([false, true])(
    'adds spacing above the pending message only when applied chips are present: %s',
    async (hasChips) => {
      const fixture = await create(hasChips ? [COUNTRY] : []);
      fixture.componentRef.setInput('pendingChanges', true);
      fixture.detectChanges();
      const element: HTMLElement = fixture.nativeElement;
      expect(element.querySelector('[role="status"]')!.classList.contains('mt-3')).toBe(hasChips);
      expect(element.querySelector('section')!.classList.contains('mb-3')).toBe(true);
      fixture.componentRef.setInput('chips', hasChips ? [] : [COUNTRY]);
      fixture.detectChanges();
      expect(element.querySelector('[role="status"]')!.classList.contains('mt-3')).toBe(!hasChips);
    },
  );

  it.each([
    ['pl', 'Zmieniono kryteria.'],
    ['en', 'Criteria changed.'],
  ])(
    'keeps the pending notice informational in %s without duplicating the panel apply action',
    async (language, message) => {
      TestBed.inject(TranslocoService).setActiveLang(language);
      const fixture = await create();
      const clear = vi.fn();
      const remove = vi.fn();
      fixture.componentInstance.clear.subscribe(clear);
      fixture.componentInstance.remove.subscribe(remove);
      expect(
        fixture.nativeElement.querySelector('[data-testid="journal-apply-criteria"]'),
      ).toBeNull();
      fixture.componentRef.setInput('pendingChanges', true);
      fixture.detectChanges();
      const element: HTMLElement = fixture.nativeElement;
      const status = element.querySelector('[role="status"]')!;
      expect(status.querySelector('span')!.textContent!.trim()).toBe(message);
      expect(status.textContent).not.toMatch(/kliknij Szukaj|click Search/);
      expect(status.querySelector('button')).toBeNull();
      expect(clear).not.toHaveBeenCalled();
      expect(remove).not.toHaveBeenCalled();
      expect(element.querySelectorAll('li')).toHaveLength(1);
      fixture.componentRef.setInput('pendingChanges', false);
      fixture.detectChanges();
      expect(element.querySelector('[data-testid="journal-apply-criteria"]')).toBeNull();
    },
  );
});
