import { TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of } from 'rxjs';
import { JournalPagination } from './journal-pagination';

class TestLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('JournalPagination', () => {
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

  function create(page = 0, total = 1204, pageSize = 10) {
    const fixture = TestBed.createComponent(JournalPagination);
    fixture.componentRef.setInput('page', page);
    fixture.componentRef.setInput('totalPages', total);
    fixture.componentRef.setInput('totalElements', 12037);
    fixture.componentRef.setInput('pageSize', pageSize);
    fixture.detectChanges();
    return fixture;
  }

  it('renders bounded numbered controls including the first and last page and accessible current page', () => {
    const fixture = create(1101);
    const component = fixture.componentInstance;
    expect(component.pages().map((entry) => entry.page)).toEqual([
      0, 1099, 1100, 1101, 1102, 1103, 1203,
    ]);
    expect(component.pages().filter((entry) => entry.gap)).toHaveLength(2);
    const current = fixture.nativeElement.querySelector('[aria-current="page"]');
    expect(current.textContent.trim()).toBe('1102');
    expect(
      [...fixture.nativeElement.querySelectorAll('#journal-page-size option')].map(
        (option) => (option as HTMLOptionElement).value,
      ),
    ).toEqual(['10', '25', '50', '100']);
  });

  it.each([10, 25, 50, 100])('shows page size %i when pagination is created and synchronizes later changes', (size) => {
    const fixture = create(0, 1204, size);
    const select = fixture.nativeElement.querySelector('#journal-page-size') as HTMLSelectElement;
    expect(select.value).toBe(String(size));
    fixture.componentRef.setInput('pageSize', size === 10 ? 25 : 10);
    fixture.detectChanges();
    expect(select.value).toBe(size === 10 ? '25' : '10');
  });

  it('supports direct navigation to any valid page without emitting one-based API pages', () => {
    const component = create().componentInstance;
    const changed = vi.fn();
    component.pageChange.subscribe(changed);
    component.jump('1102');
    component.select(1203);
    expect(changed.mock.calls).toEqual([[1101], [1203]]);
  });

  it.each(['', '-1', '0', '1205', '1.5', '1e2', 'NaN', '9007199254740992'])(
    'rejects invalid jump %s',
    (value) => {
      const component = create().componentInstance;
      const changed = vi.fn();
      component.pageChange.subscribe(changed);
      component.jump(value);
      expect(changed).not.toHaveBeenCalled();
    },
  );

  it('does not request the current page or navigate while disabled', () => {
    const fixture = create();
    const changed = vi.fn();
    fixture.componentInstance.pageChange.subscribe(changed);
    fixture.componentInstance.select(0);
    fixture.componentRef.setInput('disabled', true);
    fixture.componentInstance.select(1);
    fixture.componentInstance.jump('10');
    expect(changed).not.toHaveBeenCalled();
  });

  it('handles empty and single-page results without active navigation', () => {
    const fixture = create(0, 0);
    expect(fixture.componentInstance.pages()).toEqual([]);
    expect(
      [...fixture.nativeElement.querySelectorAll('button')].every(
        (button) => (button as HTMLButtonElement).disabled,
      ),
    ).toBe(true);
    fixture.componentRef.setInput('totalPages', 1);
    expect(fixture.componentInstance.pages().map((entry) => entry.page)).toEqual([0]);
  });

  it('converts a submitted visible page number to a zero-based output', () => {
    const fixture = create();
    const button = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(button.classList.contains('btn')).toBe(true);
    expect(button.classList.contains('btn-primary')).toBe(true);
    expect(button.classList.contains('btn-outline-primary')).toBe(false);
    const changed = vi.fn();
    fixture.componentInstance.pageChange.subscribe(changed);
    fixture.nativeElement.querySelector('input').value = '1204';
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    expect(changed).toHaveBeenCalledExactlyOnceWith(1203);
  });

  it('changes only to an allowed page size and respects loading and the current value', () => {
    const fixture = create();
    const changed = vi.fn();
    fixture.componentInstance.pageSizeChange.subscribe(changed);
    const select = fixture.nativeElement.querySelector('#journal-page-size') as HTMLSelectElement;
    select.value = '25';
    select.dispatchEvent(new Event('change'));
    expect(changed).toHaveBeenCalledExactlyOnceWith(25);
    fixture.componentRef.setInput('pageSize', 25);
    for (const value of ['25', '15', '101', '-1', 'NaN'])
      fixture.componentInstance.selectPageSize(value);
    expect(changed).toHaveBeenCalledTimes(1);
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    expect(select.disabled).toBe(true);
    fixture.componentInstance.selectPageSize('100');
    expect(changed).toHaveBeenCalledTimes(1);
  });
});
