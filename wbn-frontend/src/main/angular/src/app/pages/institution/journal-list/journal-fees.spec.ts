import { TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of } from 'rxjs';
import { JournalFees } from './journal-fees';

class TestLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('JournalFees', () => {
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

  it('shows exactly one no-fees caption and no summed or converted amounts', async () => {
    const fixture = TestBed.createComponent(JournalFees);
    fixture.componentRef.setInput('fees', { noFees: true, items: [] });
    fixture.detectChanges();
    await fixture.whenStable();
    const paragraphs = fixture.nativeElement.querySelectorAll('p');
    expect(paragraphs).toHaveLength(1);
    expect(paragraphs[0].textContent).toContain('noFees');
  });

  it('shows original amounts but only presence for Other APC and preserves zero days', async () => {
    const fixture = TestBed.createComponent(JournalFees);
    fixture.componentRef.setInput('fees', {
      noFees: false,
      items: [
        { category: 'OTHER', amount: 9999, currencyCode: 'USD' },
        { category: 'PUBLICATION', amount: 250, currencyCode: 'EUR' },
      ],
    });
    fixture.componentRef.setInput('publicationDays', 0);
    fixture.detectChanges();
    await fixture.whenStable();
    const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');
    expect(text).toContain('250 EUR');
    expect(text).not.toContain('9999');
    expect(text).not.toContain('USD');
    expect(text).toContain('feePresent');
    expect(text).toContain('0');
    expect(text).not.toContain('noFees');
  });

  it('never turns an unresolved fee into zero or a global no-fees caption', async () => {
    const fixture = TestBed.createComponent(JournalFees);
    fixture.componentRef.setInput('fees', { noFees: false, items: [{ category: 'PUBLICATION' }] });
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).not.toContain('noFees');
    expect(fixture.nativeElement.textContent).not.toContain('0');
  });

  it('uses shared description and value styles for publication time and no-fees information', async () => {
    const fixture = TestBed.createComponent(JournalFees);
    fixture.componentRef.setInput('fees', { noFees: true, items: [] });
    fixture.componentRef.setInput('publicationDays', 120);
    fixture.detectChanges();
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;
    for (const term of element.querySelectorAll('.journal-result-term')) {
      const label = term.querySelector('div > span');
      const value = term.querySelector('p');
      expect(label!.classList.contains('header-custom')).toBe(true);
      expect(label!.classList.contains('value-custom')).toBe(false);
      expect(value!.classList.contains('value-custom')).toBe(true);
      expect(value!.classList.contains('header-custom')).toBe(false);
    }
    expect(
      element.querySelector('[data-testid="journal-result-publication-time"] p')!.textContent,
    ).toContain('120');
    expect(element.querySelector('[data-testid="journal-result-fees"] p')!.textContent).toContain(
      'noFees',
    );
  });

  it('styles fee category descriptions separately from amounts and presence values', async () => {
    const fixture = TestBed.createComponent(JournalFees);
    fixture.componentRef.setInput('fees', {
      noFees: false,
      items: [{ category: 'PUBLICATION', amount: 250, currencyCode: 'EUR' }, { category: 'OTHER' }],
    });
    fixture.detectChanges();
    await fixture.whenStable();
    const values: NodeListOf<HTMLParagraphElement> = fixture.nativeElement.querySelectorAll(
      '[data-testid="journal-result-fees"] p',
    );
    expect(values).toHaveLength(2);
    for (const value of values) {
      expect(value.classList.contains('value-custom')).toBe(true);
      expect(value.querySelector('.header-custom')).not.toBeNull();
      expect(value.querySelector('.header-custom')!.classList.contains('value-custom')).toBe(false);
    }
    expect(values[0].textContent).toContain('250 EUR');
    expect(values[0].querySelector('.header-custom')!.textContent).not.toContain('250');
    expect(values[1].textContent).toContain('feePresent');
    expect(values[1].querySelector('.header-custom')!.textContent).not.toContain('feePresent');
  });

  it('uses text-sized icons and inline labels and values while allowing narrow layouts to wrap', async () => {
    const fixture = TestBed.createComponent(JournalFees);
    fixture.componentRef.setInput('fees', { noFees: true, items: [] });
    fixture.componentRef.setInput('publicationDays', 100);
    fixture.detectChanges();
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;
    expect(getComputedStyle(element.querySelector('.journal-result-terms')!).flexWrap).toBe('wrap');
    for (const term of element.querySelectorAll('.journal-result-term')) {
      const icon = term.querySelector('i')!;
      const label = term.querySelector('div > span')!;
      const value = term.querySelector('p')!;
      expect(icon.getAttribute('aria-hidden')).toBe('true');
      expect(getComputedStyle(icon).fontSize).toBe('1em');
      expect(getComputedStyle(term).alignItems).toBe('baseline');
      expect(getComputedStyle(term).flexBasis).toBe('auto');
      expect(getComputedStyle(value).display).toBe('inline');
      expect(getComputedStyle(value).whiteSpace).not.toBe('nowrap');
      expect(getComputedStyle(label).marginInlineEnd).toBe('0.4rem');
    }
    expect(
      element.querySelector('[data-testid="journal-result-publication-time"]')!.textContent,
    ).toContain('100');
    expect(element.querySelector('[data-testid="journal-result-fees"]')!.textContent).toContain(
      'noFees',
    );
  });

  it('keeps subsequent fee categories on separate lines instead of merging their descriptions', async () => {
    const fixture = TestBed.createComponent(JournalFees);
    fixture.componentRef.setInput('fees', {
      noFees: false,
      items: [
        { category: 'PUBLICATION', amount: 250, currencyCode: 'EUR' },
        { category: 'OPEN_ACCESS', amount: 100, currencyCode: 'USD' },
        { category: 'OTHER' },
      ],
    });
    fixture.detectChanges();
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;
    const values = [...element.querySelectorAll('[data-testid="journal-result-fees"] p')];
    expect(values).toHaveLength(3);
    expect(getComputedStyle(values[0]).display).toBe('inline');
    for (const value of values.slice(1)) expect(getComputedStyle(value).display).toBe('block');
    expect(values[0].textContent).toContain('250 EUR');
    expect(values[1].textContent).toContain('100 USD');
    expect(values[2].textContent).toContain('feePresent');
  });
});
