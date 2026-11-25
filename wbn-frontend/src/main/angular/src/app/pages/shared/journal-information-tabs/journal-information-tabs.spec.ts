import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader, TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';
import { InstitutionView, JournalView } from '../../../core/backend';
import { JournalInformation, JournalInformationTabs } from './journal-information-tabs';

class Loader implements TranslocoLoader {
  getTranslation(lang: string) {
    return of(lang === 'en' ? en : pl);
  }
}

const DESCRIPTION = 'Complete description without truncation.\n'.repeat(100) + 'Last sentence.';
const PUBLISHERS: InstitutionView[] = [
  {
    name: 'Publishing institution',
    street: 'Publisher street',
    email: 'publisher@example.test',
    phone: '123456789',
    www: 'https://example.test',
    country: { nameEn: 'Poland', namePl: 'Polska' },
  },
];
const JOURNAL: JournalView = {
  id: 123,
  titleOriginal: 'Original title',
  titleEnglish: 'English title',
  titleShort: 'Short title',
  kind: 'SCIENTIFIC',
  printedVersion: false,
  electronicVersion: true,
  paidArticle: { paidType: 'FREE_ARTICLES', value: 0, currencyCode: 'PLN' },
  descriptions: [
    { type: 'PL', content: DESCRIPTION },
    { type: 'EN', content: 'English ' + DESCRIPTION },
  ],
  scientificCategory: { nameEn: 'ICI main', namePl: 'ICI main' },
  scientificCategories: [{ nameEn: 'ICI other' }],
  trCategory: { nameEn: 'WoS main' },
  trCategories: [{ nameEn: 'WoS other' }],
  ajscCategory: { nameEn: 'Scimago main' },
  ajscCategories: [{ nameEn: 'Scimago other' }],
  editorInChiefs: [{ fullName: 'Chief Editor', title: 'Dr', department: 'Department' }],
  deputyEditorInChiefs: [{ fullName: 'Deputy Editor' }],
  editorialSecretaries: [{ fullName: 'Editorial Secretary' }],
  editors: [{ name: 'Editorial office', street: 'Editorial street', email: 'office@example.test' }],
  affiliationName: 'Affiliated university',
  publishers: PUBLISHERS,
};

describe('JournalInformationTabs shared by full details and decision card', () => {
  let fixture: ComponentFixture<JournalInformationTabs>;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JournalInformationTabs],
      providers: [
        provideTransloco({
          config: {
            availableLangs: ['pl', 'en'],
            defaultLang: 'pl',
            prodMode: true,
            reRenderOnLangChange: true,
          },
          loader: Loader,
        }),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(JournalInformationTabs);
    fixture.componentRef.setInput('journal', JOURNAL);
    fixture.componentRef.setInput('publishers', PUBLISHERS);
  });
  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }
  async function select(tab: number) {
    fixture.nativeElement.querySelector(`[data-testid="journal-info-tab-${tab}"]`).click();
    await settle();
  }
  function panel(): HTMLElement {
    const selected: HTMLElement = fixture.nativeElement.querySelector(
      '[role="tab"][aria-selected="true"]',
    );
    return fixture.nativeElement.querySelector(
      '[id="' + selected.getAttribute('aria-controls') + '"]',
    );
  }

  it.each(['pl', 'en'])(
    'shows the entire description and the same five tab contents (%s)',
    async (lang) => {
      TestBed.inject(TranslocoService).setActiveLang(lang);
      await settle();
      const labels = [...fixture.nativeElement.querySelectorAll('[role="tab"]')].map(
        (value: Element) => value.textContent?.trim(),
      );
      expect(labels).toEqual(
        lang === 'pl'
          ? ['Opis czasopisma', 'Szczegóły', 'Profil naukowy', 'Redakcja', 'Wydawca']
          : [
              'Journal description',
              'Details',
              'Scientific profile',
              'Editorial board',
              'Publisher',
            ],
      );
      expect(panel().textContent).toContain(lang === 'pl' ? DESCRIPTION : 'English ' + DESCRIPTION);
      await select(2);
      expect(panel().textContent).toContain('Short title');
      expect(panel().textContent).toContain(lang === 'pl' ? 'Nie' : 'No');
      expect(panel().textContent).toContain(lang === 'pl' ? 'Tak' : 'Yes');
      expect(panel().textContent?.replace(/\s+/g, ' ')).toContain('0 PLN');
      await select(3);
      for (const label of [
        'ICI JML',
        'ICI main',
        'ICI other',
        'Web of Science',
        'WoS main',
        'WoS other',
        'Scimago main',
        'Scimago other',
      ]) {
        expect(panel().textContent).toContain(label);
      }
      await select(4);
      for (const label of [
        'Chief Editor',
        'Deputy Editor',
        'Editorial Secretary',
        'Editorial office',
        'Editorial street',
        'office@example.test',
      ]) {
        expect(panel().textContent).toContain(label);
      }
      await select(5);
      for (const label of [
        'Publishing institution',
        'Publisher street',
        'publisher@example.test',
        '123456789',
        'Affiliated university',
      ]) {
        expect(panel().textContent).toContain(label);
      }
      expect(panel().querySelector('a')?.getAttribute('href')).toBe('https://example.test');
      await select(1);
      expect(panel().textContent).toContain('Last sentence.');
    },
  );

  it('preserves a tab on data refresh and resets it for another journal', async () => {
    await settle();
    await select(5);
    fixture.componentRef.setInput('journal', { ...JOURNAL, affiliationName: 'Updated university' });
    await settle();
    expect(fixture.componentInstance.activeTab()).toBe(5);
    expect(panel().textContent).toContain('Updated university');
    fixture.componentRef.setInput('journal', { ...JOURNAL, id: 456 });
    await settle();
    expect(fixture.componentInstance.activeTab()).toBe(1);
  });

  it('handles missing catalogue data without inventing false values or contacts', async () => {
    const empty: JournalInformation = {
      id: 123,
      printedVersion: null,
      electronicVersion: null,
      editorInChiefs: null,
      deputyEditorInChiefs: null,
      editorialSecretaries: null,
      editors: null,
      scientificCategory: null,
      scientificCategories: null,
      trCategories: null,
      ajscCategories: null,
    };
    fixture.componentRef.setInput('journal', empty);
    fixture.componentRef.setInput('publishers', null);
    await settle();
    await select(2);
    expect(panel().textContent).not.toContain('Nie');
    await select(3);
    expect(panel().textContent).toContain('ICI JML');
    await select(4);
    expect(panel().querySelectorAll('app-person-data, app-institution-data')).toHaveLength(0);
    await select(5);
    expect(panel().querySelectorAll('app-institution-data')).toHaveLength(0);
  });
});
