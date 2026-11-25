import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader, TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import pl from '../../../../../assets/i18n/pl.json';
import en from '../../../../../assets/i18n/en.json';
import { PublishingVersions } from './publishing-versions/publishing-versions';
import { Standards } from './standards/standards';
import { ForAuthors } from './for-authors/for-authors';
import { Digitalization } from './digitalization/digitalization';

class Loader implements TranslocoLoader {
  getTranslation(language: string) {
    return of(language === 'en' ? en : pl);
  }
}

const metadata = [
  'ORCID', 'GICID', 'NONE', 'SHARING_POLICY', 'CONFLICT_OF_INTEREST', 'AUTHOR_STATEMENT',
  'PUBLICATION_DATE', 'ACCEPTED_DATE', 'SEND_DATE', 'AUTHOR_AFFILIATION', 'ENGLISH_KEYWORDS',
  'NATIONAL_KEYWORDS', 'DOI',
];

const reviewStandards = () => ({
  reviewRules: ['PUBLISHED_REVIEWERS_LIST', 'PUBLIC_FORM', 'PUBLIC_CRITERIA'],
  publishedReviewersListWww: 'https://example.org/reviewers',
  publicFormWww: 'https://example.org/form',
  publicCriteriaWww: 'https://example.org/criteria',
  internationalPoliciesAdopted: true,
  internationalPoliciesWww: 'https://example.org/policies',
});

const websiteInformation = () => ({
  directors: true,
  directorsWww: 'https://example.org/board',
  editOfficeInfo: true,
  editOfficeInfoWww: 'https://example.org/editors',
  antiAbusePolicyInfo: true,
  antiAbusePolicyInfoWww: 'https://example.org/anti-abuse',
});

describe('Questionnaire presentation for 2024 and 2025', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PublishingVersions, Standards, ForAuthors, Digitalization],
      providers: [
        provideTransloco({
          config: {
            availableLangs: ['pl', 'en'], defaultLang: 'pl', prodMode: true,
            reRenderOnLangChange: true,
          },
          loader: Loader,
        }),
      ],
    }).compileComponents();
  });

  async function render<T>(type: Type<T>, inputs: Record<string, unknown>): Promise<ComponentFixture<T>> {
    const fixture = TestBed.createComponent(type);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  function root<T>(fixture: ComponentFixture<T>): HTMLElement {
    return fixture.nativeElement;
  }

  function find(element: HTMLElement, selector: string): HTMLElement {
    const found = element.querySelector<HTMLElement>(selector);
    if (!found) throw new Error(`Missing element: ${selector}`);
    return found;
  }

  function value(element: HTMLElement, testId: string): string {
    const row = find(element, `[data-testid="${testId}"]`);
    return (row.querySelector('.value-custom') ?? row.lastElementChild)?.textContent?.trim() ?? '';
  }

  it.each(['array', 'set'])('orders both metadata lists from the workbook (%s)', async (collection) => {
    const source = collection === 'set' ? new Set(metadata) : [...metadata];
    const fixture = await render(PublishingVersions, {
      year: 2025,
      publishingVersions: { printedVersion: true, electronicArticleMetadata: source, printedArticleMetadata: source },
    });
    const expected = [
      'Słowa kluczowe w języku oryginalnym', 'Słowa kluczowe w języku angielskim', 'Afiliacja autora',
      'Data przesłania artykułu do redakcji', 'Data akceptacji artykułu przez redakcję',
      'Data opublikowania artykułu',
      'Oświadczenie autora o źródłach finansowania badań przedstawionych w artykule naukowym lub powstania artykułu naukowego',
      'Informacja o konflikcie interesów', 'Zasady udostępniania pracy', 'DOI', 'ORCID',
    ];
    for (const id of ['electronic-article-metadata', 'printed-article-metadata']) {
      expect([...find(root(fixture), `[data-testid="${id}"] .value-custom`).children]
        .map((item) => item.textContent?.trim())).toEqual(expected);
    }
    expect(Array.from(source)).toEqual(metadata);
  });

  it.each([2024, 2026])('hides new metadata, GICID and NONE for %i', async (year) => {
    const fixture = await render(PublishingVersions, {
      year,
      publishingVersions: { printedVersion: true, electronicArticleMetadata: metadata, printedArticleMetadata: metadata },
    });
    for (const id of ['electronic-article-metadata', 'printed-article-metadata']) {
      const row = find(root(fixture), `[data-testid="${id}"] .value-custom`);
      expect(row.children).toHaveLength(8);
      expect(row.textContent).not.toContain('GICID');
      expect(row.textContent).not.toContain('Data opublikowania');
      expect(row.textContent).not.toContain('konflikcie interesów');
      expect(row.textContent).not.toContain('Zasady udostępniania');
    }
  });

  it('shows a dash when all supplied metadata options are hidden', async () => {
    const fixture = await render(PublishingVersions, {
      year: 2025,
      publishingVersions: { printedVersion: true, electronicArticleMetadata: ['GICID', 'NONE'], printedArticleMetadata: null },
    });
    expect(value(root(fixture), 'electronic-article-metadata')).toBe('-');
    expect(value(root(fixture), 'printed-article-metadata')).toBe('-');
  });

  it.each([
    [true, 12, '12'], [true, 0, '0'], [true, null, '-'], [true, undefined, '-'],
    [false, 12, '-'], [undefined, 12, '-'],
  ])('shows AOP count for flag %s and value %s', async (aop, count, expected) => {
    const fixture = await render(PublishingVersions, {
      year: 2025, publishingVersions: { aop, aopArticlesCount: count, printedVersion: false },
    });
    expect(value(root(fixture), 'aop-articles-count')).toBe(expected);
    const aopRow = find(root(fixture), '[data-testid="aop"]');
    expect(aopRow.nextElementSibling?.getAttribute('data-testid')).toBe('aop-articles-count');
    expect(aopRow.parentElement?.previousElementSibling?.textContent).toContain('Wersja online');
    expect(find(root(fixture), '[data-testid="aop-articles-count"]').textContent)
      .toContain('Liczba artykułów opublikowanych w trybie "ahead of print" / "early birds" w 2025 roku');
  });

  it('preserves the AOP answer in 2024 while showing a dash for the count', async () => {
    const fixture = await render(PublishingVersions, {
      year: 2024, publishingVersions: { aop: true, aopArticlesCount: 12 },
    });
    expect(value(root(fixture), 'aop')).toBe('Tak');
    expect(value(root(fixture), 'aop-articles-count')).toBe('-');
  });

  it.each(['array', 'set'])('places review links in description cells, preserving statuses (%s)', async (collection) => {
    const source = reviewStandards();
    const fixture = await render(Standards, {
      year: 2025, standards: {
        ...source, reviewRules: collection === 'set' ? new Set(source.reviewRules) : source.reviewRules,
      },
    });
    for (const [id, url] of [
      ['reviewers-list-rule', source.publishedReviewersListWww],
      ['public-form-rule', source.publicFormWww], ['public-criteria-rule', source.publicCriteriaWww],
    ]) {
      const row = find(root(fixture), `[data-testid="${id}"]`);
      expect(row.children).toHaveLength(2);
      const link = find(row.children[0] as HTMLElement, 'a');
      expect(link.getAttribute('href')).toBe(url);
      expect(link.parentElement?.tagName).toBe('DIV');
      expect(row.children[1].textContent?.trim()).toBe('Tak');
      expect(row.children[1].querySelector('a')).toBeNull();
    }
    expect(find(root(fixture), '[data-testid="international-policies"] a').getAttribute('href'))
      .toBe(source.internationalPoliciesWww);
  });

  it.each([null, undefined, '', '   '])('omits blank review links (%s) without changing Yes', async (url) => {
    const fixture = await render(Standards, {
      year: 2025, standards: { ...reviewStandards(), publishedReviewersListWww: url, publicFormWww: url, publicCriteriaWww: url },
    });
    for (const id of ['reviewers-list-rule', 'public-form-rule', 'public-criteria-rule']) {
      const row = find(root(fixture), `[data-testid="${id}"]`);
      expect(row.children[0].querySelector('div')).toBeNull();
      expect(row.children[1].textContent?.trim()).toBe('Tak');
    }
  });

  it('does not show URLs for unselected review rules or policies', async () => {
    const fixture = await render(Standards, {
      year: 2025, standards: { ...reviewStandards(), reviewRules: [], internationalPoliciesAdopted: false },
    });
    expect(root(fixture).querySelector('a')).toBeNull();
    expect(value(root(fixture), 'reviewers-list-rule')).toBe('Nie');
    expect(find(root(fixture), '[data-testid="international-policies"]').textContent?.trim()).toBe('-');
  });

  it('preserves Yes when international policies have no URL', async () => {
    const fixture = await render(Standards, {
      year: 2025, standards: { internationalPoliciesAdopted: true, internationalPoliciesWww: '  ' },
    });
    const field = find(root(fixture), '[data-testid="international-policies"]');
    expect(field.textContent?.trim()).toBe('Tak');
    expect(field.querySelector('a')).toBeNull();
  });

  it('shows no new links and a policy dash in 2024', async () => {
    const fixture = await render(Standards, { year: 2024, standards: reviewStandards() });
    expect(root(fixture).querySelector('a')).toBeNull();
    expect(value(root(fixture), 'reviewers-list-rule')).toBe('Tak');
    expect(find(root(fixture), '[data-testid="international-policies"]').textContent?.trim()).toBe('-');
  });

  it('updates statuses and links when switching survey years and data', async () => {
    const fixture = await render(Standards, { year: 2025, standards: reviewStandards() });
    fixture.componentRef.setInput('year', 2024);
    fixture.componentRef.setInput('standards', { ...reviewStandards(), reviewRules: [] });
    fixture.detectChanges();
    expect(value(root(fixture), 'reviewers-list-rule')).toBe('Nie');
    expect(root(fixture).querySelector('a')).toBeNull();
    fixture.componentRef.setInput('year', 2025);
    fixture.componentRef.setInput('standards', reviewStandards());
    fixture.detectChanges();
    expect(value(root(fixture), 'reviewers-list-rule')).toBe('Tak');
    expect(root(fixture).querySelectorAll('a')).toHaveLength(4);
  });

  it.each([
    [true, true], [true, false], [false, true], [false, false],
  ])('presents the two AI answers independently (%s, %s)', async (scope, instructions) => {
    const fixture = await render(ForAuthors, {
      year: 2025, standards: {
        statements: scope ? ['AI_USAGE_SCOPE'] : [],
        authorInstructions: instructions ? ['AI_USAGE_INSTRUCTIONS'] : [],
      },
    });
    expect(value(root(fixture), 'ai-usage-scope')).toBe(scope ? 'Tak' : 'Nie');
    expect(value(root(fixture), 'ai-usage-instructions')).toBe(instructions ? 'Tak' : 'Nie');
    expect(find(root(fixture), '[data-testid="ai-usage-scope"]').previousElementSibling?.textContent)
      .toContain('wskazanie źródeł finansowania');
    expect(find(root(fixture), '[data-testid="ai-usage-instructions"]').previousElementSibling?.textContent)
      .toContain('wytyczne dotyczące przygotowania piśmiennictwa');
  });

  it('supports Set options and resets AI answers to dashes in 2024', async () => {
    const fixture = await render(ForAuthors, {
      year: 2025, standards: { statements: new Set(['AI_USAGE_SCOPE', 'FINANCE_SOURCE']), authorInstructions: new Set(['AI_USAGE_INSTRUCTIONS']) },
    });
    expect(value(root(fixture), 'ai-usage-scope')).toBe('Tak');
    expect(value(root(fixture), 'ai-usage-instructions')).toBe('Tak');
    fixture.componentRef.setInput('year', 2024);
    fixture.componentRef.setInput('standards', {});
    fixture.detectChanges();
    for (const id of ['ai-usage-scope', 'ai-usage-instructions']) {
      expect(value(root(fixture), id)).toBe('-');
      expect(find(root(fixture), `[data-testid="${id}"]`).querySelector('i')).toBeNull();
    }
    expect(find(root(fixture), '[data-testid="ai-usage-scope"]').previousElementSibling?.lastElementChild?.textContent?.trim()).toBe('Nie');
    fixture.componentRef.setInput('year', 2025);
    fixture.detectChanges();
    expect(value(root(fixture), 'ai-usage-scope')).toBe('Nie');
    expect(value(root(fixture), 'ai-usage-instructions')).toBe('Nie');
  });

  it('places website links directly under their matching descriptions in 2025', async () => {
    const fixture = await render(Digitalization, { year: 2025, digitalization: { infoWebsite: websiteInformation() } });
    const links = find(root(fixture), '[data-testid="website-information"]').querySelectorAll('a');
    expect([...links].map((link) => link.getAttribute('href'))).toEqual([
      'https://example.org/board', 'https://example.org/editors', 'https://example.org/anti-abuse',
    ]);
    expect(links[0].previousElementSibling?.textContent).toBe('wykaz członków Rady Naukowej');
    expect(links[1].previousElementSibling?.textContent).toBe('dane redakcji');
    expect(links[2].previousElementSibling?.textContent).toBe('polityka przeciwdziałania nadużyciom');
  });

  it('keeps website descriptions without blank link lines', async () => {
    const fixture = await render(Digitalization, {
      year: 2025, digitalization: { infoWebsite: { ...websiteInformation(), directorsWww: null, editOfficeInfoWww: '', antiAbusePolicyInfoWww: ' ' } },
    });
    const row = find(root(fixture), '[data-testid="website-information"]');
    expect(row.querySelector('a')).toBeNull();
    expect(row.textContent).toContain('wykaz członków Rady Naukowej');
    expect(row.textContent).toContain('dane redakcji');
    expect(row.textContent).toContain('polityka przeciwdziałania nadużyciom');
  });

  it('omits website options and links when flags are false', async () => {
    const fixture = await render(Digitalization, {
      year: 2025, digitalization: { infoWebsite: { ...websiteInformation(), directors: false, editOfficeInfo: false, antiAbusePolicyInfo: false } },
    });
    const row = find(root(fixture), '[data-testid="website-information"]');
    expect(row.querySelector('a')).toBeNull();
    expect(row.textContent).not.toContain('polityka przeciwdziałania nadużyciom');
  });

  it('removes extra website lines when switching from 2025 to 2024', async () => {
    const fixture = await render(Digitalization, {
      year: 2025, digitalization: { infoWebsite: websiteInformation(), editorialTools: ['STATISTICS'] },
    });
    const statisticsRow = [...root(fixture).querySelectorAll('tr')]
      .find((row) => row.textContent?.includes('Czasopismo gromadzi statystyki'))!;
    expect(statisticsRow.lastElementChild?.textContent?.trim()).toBe('Tak');
    fixture.componentRef.setInput('year', 2024);
    fixture.componentRef.setInput('digitalization', { infoWebsite: websiteInformation(), editorialTools: [] });
    fixture.detectChanges();
    const row = find(root(fixture), '[data-testid="website-information"]');
    expect(row.querySelector('a')).toBeNull();
    expect(row.textContent).not.toContain('polityka przeciwdziałania nadużyciom');
    expect(row.textContent).toContain('wykaz członków Rady Naukowej');
    expect(row.textContent).toContain('dane redakcji');
    expect(statisticsRow.lastElementChild?.textContent?.trim()).toBe('Nie');
  });

  it('does not apply the 2025 extension to 2026', async () => {
    const publishing = await render(PublishingVersions, { year: 2026, publishingVersions: { aop: true, aopArticlesCount: 12 } });
    const standards = await render(Standards, { year: 2026, standards: reviewStandards() });
    const authors = await render(ForAuthors, { year: 2026, standards: { statements: ['AI_USAGE_SCOPE'], authorInstructions: ['AI_USAGE_INSTRUCTIONS'] } });
    const digitalization = await render(Digitalization, { year: 2026, digitalization: { infoWebsite: websiteInformation() } });
    expect(root(publishing).querySelector('[data-testid="aop-articles-count"]')).toBeNull();
    expect(root(standards).querySelector('[data-testid="international-policies"]')).toBeNull();
    expect(root(standards).querySelector('a')).toBeNull();
    expect(root(authors).querySelector('[data-testid="ai-usage-scope"]')).toBeNull();
    expect(root(authors).querySelector('[data-testid="ai-usage-instructions"]')).toBeNull();
    expect(root(digitalization).querySelector('a')).toBeNull();
    expect(root(digitalization).textContent).not.toContain('polityka przeciwdziałania nadużyciom');
  });

  it('renders the new labels and answer states in English', async () => {
    TestBed.inject(TranslocoService).setActiveLang('en');
    const publishing = await render(PublishingVersions, { year: 2025, publishingVersions: { aop: true, aopArticlesCount: 0 } });
    const standards = await render(Standards, { year: 2025, standards: { internationalPoliciesAdopted: true } });
    const authors = await render(ForAuthors, { year: 2025, standards: { statements: ['AI_USAGE_SCOPE'] } });
    const digitalization = await render(Digitalization, { year: 2025, digitalization: { infoWebsite: websiteInformation() } });
    expect(root(publishing).textContent).toContain('Number of articles published in "ahead of print" / "early birds" mode in 2025');
    expect(root(standards).textContent).toContain('Adopted international publishing policies');
    expect(find(root(standards), '[data-testid="international-policies"]').textContent?.trim()).toBe('Yes');
    expect(value(root(authors), 'ai-usage-scope')).toBe('Yes');
    expect(value(root(authors), 'ai-usage-instructions')).toBe('No');
    expect(root(authors).textContent).toContain('guidelines on the permitted scope of AI tool use');
    expect(root(digitalization).textContent).toContain('anti-abuse policy');
  });
});
