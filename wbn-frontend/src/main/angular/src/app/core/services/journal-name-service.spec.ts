import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';

import { JournalView } from '../backend';
import { JournalNameService } from './journal-name-service';

describe('JournalNameService', () => {
  let service: JournalNameService;
  let translocoService: {
    getActiveLang: ReturnType<typeof vi.fn>;
    translate: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    translocoService = {
      getActiveLang: vi.fn(() => 'pl'),
      translate: vi.fn(() => 'nd'),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: TranslocoService, useValue: translocoService }],
    });
    service = TestBed.inject(JournalNameService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return journal description content for active language', () => {
    const journal: JournalView = {
      descriptions: [
        { type: 'EN', content: 'English description' },
        { type: 'PL', content: 'Polski opis' },
      ],
    };

    expect(service.getJournalDescription(journal)).toBe('Polski opis');
  });

  it('should compare active language and description type in lower case', () => {
    translocoService.getActiveLang.mockReturnValue('en');
    const journal: JournalView = {
      descriptions: [{ type: 'EN', content: 'English description' }],
    };

    expect(service.getJournalDescription(journal)).toBe('English description');
  });

  it('should return English journal description when active language content is missing', () => {
    const journal: JournalView = {
      descriptions: [
        { type: 'EN', content: 'English description' },
        { type: 'PL' },
      ],
    };

    expect(service.getJournalDescription(journal)).toBe('English description');
  });

  it('should return translated no-data value when descriptions are missing', () => {
    expect(service.getJournalDescription({})).toBe('nd');
    expect(service.getJournalDescription(null)).toBe('nd');
  });

  it('should return English country name when active language name is missing', () => {
    expect(service.getCountryName({ nameEn: 'Poland' })).toBe('Poland');
  });

  it('should return English language name when active language name is missing', () => {
    translocoService.getActiveLang.mockReturnValue('ru');

    expect(service.getLanguageName({ nameEn: 'English' })).toBe('English');
  });

  it('should translate digitalization publishing tool ids', () => {
    service.getJournalDictionaryItem('ICI');

    expect(translocoService.translate).toHaveBeenCalledWith(
      'journal.dictionary.digitalization.publishing-tools.ici',
    );
  });

  it.each([
    ['FOR_ALL', 'for-all'],
    ['INTERNAL_AUTHORS', 'internal-authors'],
    ['EXTERNAL_AUTHORS', 'external-authors'],
  ])('should translate fee description code %s', (description, label) => {
    translocoService.translate.mockReturnValue('Translated fee description');

    expect(service.getAuthorsFeeDetailDescription(description)).toBe('Translated fee description');
    expect(translocoService.translate).toHaveBeenCalledWith(
      `journal.dictionary.authors-fee.detail-description.${label}`,
    );
  });

  it.each(['Polska', 'Zagranica', 'Custom fee description', 'NO_FEE', 'Nature'])(
    'should preserve free-text fee description %s',
    (description) => {
      expect(service.getAuthorsFeeDetailDescription(description)).toBe(description);
      expect(translocoService.translate).not.toHaveBeenCalled();
    },
  );

  it.each([undefined, null, '', '   '])(
    'should return a placeholder for empty fee description %s',
    (description) => {
      expect(service.getAuthorsFeeDetailDescription(description)).toBe('-');
      expect(translocoService.translate).not.toHaveBeenCalled();
    },
  );

  it.each([
    ['PUBLICATION_DATE', 'journal.dictionary.article-metadata.publication-date'],
    ['CONFLICT_OF_INTEREST', 'journal.dictionary.article-metadata.conflict-of-interest'],
    ['SHARING_POLICY', 'journal.dictionary.article-metadata.sharing-policy'],
    ['AI_USAGE_SCOPE', 'journal.dictionary.standards.statements.ai-usage-scope'],
    ['AI_USAGE_INSTRUCTIONS', 'journal.dictionary.standards.author-instructions.ai-usage-instructions'],
  ])('should translate imported survey option %s', (option, translationKey) => {
    service.getJournalDictionaryItem(option);

    expect(translocoService.translate).toHaveBeenCalledWith(translationKey);
  });
});
