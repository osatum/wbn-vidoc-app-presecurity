import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AuthorsFee, StandardsDto } from '../../../../../core/backend';
import { translocoTestingModule } from '../../../../../testing/transloco-testing-module';
import { ForAuthors } from './for-authors';

describe('ForAuthors', () => {
  let component: ForAuthors;
  let fixture: ComponentFixture<ForAuthors>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ForAuthors, translocoTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(ForAuthors);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it.each(['submissionFee', 'publicationFee', 'openAccessFee', 'rapidReview', 'apc'] as const)(
    'shows the selected range for %s and preserves the entire tariff',
    (feeName) => {
      const fee: AuthorsFee = {
        feeType: 'COUNTRY_SPECIFIC_FEE',
        feeDetails: [
          { description: 'EXTERNAL_AUTHORS', minValue: 1000, maxValue: 1500, currencyCode: 'PLN' },
          { description: 'INTERNAL_AUTHORS', minValue: 500, maxValue: 700, currencyCode: 'PLN' },
        ],
        countrySpecificFeeSelection: {
          authorCountryCode: 'PL',
          effectiveVariant: 'EXTERNAL_AUTHORS',
          overrideVariant: 'EXTERNAL_AUTHORS',
          source: 'JOURNAL_OVERRIDE',
        },
        feeSelectionStatus: 'RESOLVED',
        selectedFeeDetail: { minValue: 1000, maxValue: 1500, currencyCode: 'PLN' },
      };
      const standards: StandardsDto = { [feeName]: fee };
      fixture.componentRef.setInput('standards', standards);
      fixture.detectChanges();

      const element: HTMLElement = fixture.nativeElement;
      const selected = element.querySelector('[data-testid="fee-selection"]');
      expect(selected?.textContent).toContain('1000');
      expect(selected?.textContent).toContain('1500');
      expect(selected?.textContent).toContain('PLN');
      expect(element.textContent).toContain('500');
      expect(element.textContent).toContain('700');
      expect(element.textContent).toContain('JOURNAL_OVERRIDE');
    },
  );

  it.each(['UNRESOLVED', 'INCOMPLETE', 'INVALID', 'LEGACY'] as const)(
    'shows %s without an applicable amount',
    (status) => {
      fixture.componentRef.setInput('standards', {
        publicationFee: {
          feeType: 'COUNTRY_SPECIFIC_FEE',
          feeSelectionStatus: status,
          feeDetails: [{ description: 'INTERNAL_AUTHORS', minValue: 500, maxValue: 700 }],
          selectedFeeDetail: { minValue: 0, maxValue: 0 },
        },
      });
      fixture.detectChanges();

      const element: HTMLElement = fixture.nativeElement;
      const selected = element.querySelector('[data-testid="fee-selection"]');
      expect(selected?.textContent).toContain(status);
      expect(selected?.querySelector('td')?.getAttribute('colspan')).toBe('3');
      expect(selected?.textContent).not.toContain('0');
      expect(element.textContent).toContain('500');
    },
  );

  it('keeps legacy tariffs without inferring an applicable variant', () => {
    fixture.componentRef.setInput('standards', {
      publicationFee: { feeType: 'COUNTRY_SPECIFIC_FEE', feeDetails: [] },
    });
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('[data-testid="fee-selection"]')?.textContent).toContain('LEGACY');
  });

  it('distinguishes no fee from missing fee information', () => {
    fixture.componentRef.setInput('standards', {
      publicationFee: { feeType: 'NO_FEE' },
      apc: { feeDetails: [] },
    });
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain('journal.dictionary.authors-fee.no-fee');
    expect(element.querySelector('[data-testid="fee-selection"]')).toBeNull();
  });

  it('should render free-text and dictionary fee descriptions using the real name service', async () => {
    fixture.componentRef.setInput('standards', {
      publicationFee: {
        feeType: AuthorsFee.FeeTypeEnum.VariousFee,
        feeDetails: [
          { description: 'Polska', minValue: 100, maxValue: 200, currencyCode: 'PLN' },
          { description: 'Zagranica', minValue: 50, maxValue: 100, currencyCode: 'EUR' },
          { description: 'FOR_ALL', minValue: 10, maxValue: 20, currencyCode: 'USD' },
          { minValue: 1, maxValue: 2, currencyCode: 'GBP' },
        ],
      },
    });

    await fixture.whenStable();

    const feeRows = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tr'),
    ).filter((row) => row.querySelectorAll('td').length === 4);

    expect(feeRows.map((row) => row.cells[0].textContent?.trim())).toEqual([
      'Polska',
      'Zagranica',
      'journal.dictionary.authors-fee.detail-description.for-all',
      '-',
    ]);
    expect(feeRows[0].cells[1].textContent?.trim()).toBe('100');
    expect(feeRows[0].cells[2].textContent?.trim()).toBe('200');
    expect(feeRows[0].cells[3].textContent?.trim()).toBe('PLN');
  });
});
