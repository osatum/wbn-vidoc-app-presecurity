import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ArticlePublished } from '../../../../../core/backend';
import { JournalNameService } from '../../../../../core/services/journal-name-service';
import { translocoTestingModule } from '../../../../../testing/transloco-testing-module';
import { Stability } from './stability';

describe('Stability', () => {
  let component: Stability;
  let fixture: ComponentFixture<Stability>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Stability, translocoTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(Stability);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows years in ascending order and keeps every metric aligned without modifying input', () => {
    const frequencies: ArticlePublished.FrequencyCodeEnum[] = ['a', 'b', 'c', 'e'];
    const articles: ArticlePublished[] = [2024, 2021, 2023, 2022].map((year) => ({
      year,
      amount: year - 2000,
      scientificAmount: year - 2001,
      receivedManuscriptCount: year - 1990,
      rejectedManuscriptCount: year - 2020,
      publishedIssuesAmount: year - 2020,
      frequencyCode: frequencies[year - 2021],
    }));
    Object.freeze(articles);
    vi.spyOn(TestBed.inject(JournalNameService), 'getJournalDictionaryItem').mockImplementation(
      (value) => String(value),
    );
    fixture.componentRef.setInput('stability', { articlePublished: articles });
    fixture.detectChanges();

    expect(displayedYears()).toEqual(['2021', '2022', '2023', '2024']);
    const element: HTMLElement = fixture.nativeElement;
    const table = element.querySelector('table');
    const rows = Array.from(table?.querySelectorAll('tbody tr') ?? []).map((row) =>
      Array.from(row.querySelectorAll('td')).map((cell) => cell.textContent?.trim()),
    );
    expect(rows).toEqual([
      ['21', '22', '23', '24'],
      ['20', '21', '22', '23'],
      ['31', '32', '33', '34'],
      ['1', '2', '3', '4'],
      ['1', '2', '3', '4'],
      ['a', 'b', 'c', 'e'],
    ]);
    expect(articles.map((article) => article.year)).toEqual([2024, 2021, 2023, 2022]);
  });

  it('sorts numerically when the questionnaire input changes', () => {
    fixture.componentRef.setInput('stability', {
      articlePublished: [{ year: 2024 }, { year: 2021 }],
    });
    fixture.detectChanges();
    expect(displayedYears()).toEqual(['2021', '2024']);

    fixture.componentRef.setInput('stability', {
      articlePublished: [{ year: 10000 }, { year: 999 }, { year: 2023 }],
    });
    fixture.detectChanges();

    expect(displayedYears()).toEqual(['999', '2023', '10000']);
  });

  it('keeps entries without a year at the end and preserves zero values', () => {
    fixture.componentRef.setInput('stability', {
      articlePublished: [{ amount: 0 }, { year: 2024, amount: 40 }, { year: 2021, amount: 10 }],
    });
    fixture.detectChanges();

    expect(displayedYears()).toEqual(['2021', '2024', '']);
    const element: HTMLElement = fixture.nativeElement;
    const table = element.querySelector('table');
    const amounts = Array.from(table?.querySelectorAll('tbody tr:first-child td') ?? []).map(
      (cell) => cell.textContent?.trim(),
    );
    expect(amounts).toEqual(['10', '40', '0']);
  });

  it.each([{}, { articlePublished: [] }])(
    'handles missing or empty annual data: %j',
    (stability) => {
      fixture.componentRef.setInput('stability', stability);
      fixture.detectChanges();

      expect(displayedYears()).toEqual([]);
    },
  );

  it('removes the table when the stability input is cleared', () => {
    fixture.componentRef.setInput('stability', { articlePublished: [{ year: 2024 }] });
    fixture.detectChanges();
    expect(displayedYears()).toEqual(['2024']);

    fixture.componentRef.setInput('stability', undefined);
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('table')).toBeNull();
  });

  function displayedYears(): (string | undefined)[] {
    const element: HTMLElement = fixture.nativeElement;
    const table = element.querySelector('table');
    return Array.from(table?.querySelectorAll('thead th') ?? [])
      .slice(1)
      .map((cell) => cell.textContent?.trim());
  }
});
