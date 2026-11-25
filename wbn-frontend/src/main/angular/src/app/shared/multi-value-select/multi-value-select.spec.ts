import { ComponentFixture, TestBed } from '@angular/core/testing';
import type { Mock } from 'vitest';

import { MultiValueSelect } from './multi-value-select';

describe('MultiValueSelect', () => {
  let fixture: ComponentFixture<MultiValueSelect>;
  let component: MultiValueSelect;
  let changes: Mock<(value: string[]) => void>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [MultiValueSelect] });
    fixture = TestBed.createComponent(MultiValueSelect);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('label', 'Country');
    fixture.componentRef.setInput('removeLabel', 'Remove selection');
    fixture.componentRef.setInput('options', [
      { code: 'PL', label: 'Poland' },
      { code: 'DE', label: 'Germany' },
    ]);
    changes = vi.fn<(value: string[]) => void>();
    component.valueChange.subscribe(changes);
    fixture.detectChanges();
  });

  it('adds one option without mutating the supplied selection', () => {
    const selected = ['pl'];
    fixture.componentRef.setInput('value', selected);
    fixture.detectChanges();
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    select.value = 'DE';

    select.dispatchEvent(new Event('change'));

    expect(changes).toHaveBeenCalledExactlyOnceWith(['pl', 'DE']);
    expect(selected).toEqual(['pl']);
    expect(select.value).toBe('');
  });

  it('hides already selected options regardless of code case', () => {
    fixture.componentRef.setInput('value', ['pl']);
    fixture.detectChanges();

    expect(component.availableOptions().map((option) => option.code)).toEqual(['DE']);
    expect(component.selectedOptions()).toEqual([{ code: 'pl', label: 'Poland' }]);
    expect(fixture.nativeElement.querySelector('select').getAttribute('aria-label')).toBe(
      'Country',
    );
  });

  it('removes only the selected item without submitting the parent form', () => {
    fixture.componentRef.setInput('value', ['pl', 'de']);
    fixture.detectChanges();
    const remove = fixture.nativeElement.querySelector('button') as HTMLButtonElement;

    remove.click();

    expect(remove.type).toBe('button');
    expect(remove.getAttribute('aria-label')).toBe('Remove selection: Poland');
    expect(changes).toHaveBeenCalledExactlyOnceWith(['de']);
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('select'));
  });

  it('retains selections when the dictionary is unavailable or arrives later', () => {
    fixture.componentRef.setInput('options', []);
    fixture.componentRef.setInput('value', ['pl']);
    fixture.detectChanges();
    expect(component.selectedOptions()).toEqual([{ code: 'pl', label: 'pl' }]);

    fixture.componentRef.setInput('options', [{ code: 'PL', label: 'Poland' }]);
    fixture.detectChanges();

    expect(component.selectedOptions()).toEqual([{ code: 'pl', label: 'Poland' }]);
    expect(changes).not.toHaveBeenCalled();
  });

  it('allows the parent to display selections elsewhere without changing selected or available options', () => {
    fixture.componentRef.setInput('value', ['pl']);
    fixture.componentRef.setInput('showSelection', false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('ul')).toBeNull();
    expect(component.selectedOptions()).toEqual([{ code: 'pl', label: 'Poland' }]);
    expect(component.availableOptions().map((option) => option.code)).toEqual(['DE']);
    expect(changes).not.toHaveBeenCalled();
  });

  it('shows static counts including zero without inventing an unavailable count', () => {
    fixture.componentRef.setInput('options', [
      { code: 'PL', label: 'Poland', count: 12 },
      { code: 'DE', label: 'Germany', count: 0 },
      { code: 'FR', label: 'France' },
    ]);
    fixture.detectChanges();
    const labels = Array.from(
      fixture.nativeElement.querySelectorAll('select option') as NodeListOf<HTMLOptionElement>,
    ).map((option) => option.textContent?.trim());
    expect(labels).toEqual(['Country', 'Poland (12)', 'Germany (0)', 'France']);
    expect(changes).not.toHaveBeenCalled();
  });

  it('does not add a placeholder or an unknown code', () => {
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    select.dispatchEvent(new Event('change'));
    select.value = 'UNKNOWN';
    select.dispatchEvent(new Event('change'));

    expect(changes).not.toHaveBeenCalled();
  });

  it('allows an optional control height without changing other selectors or emitting a selection', () => {
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    expect(select.style.height).toBe('');
    fixture.componentRef.setInput('controlHeight', '48px');
    fixture.detectChanges();
    expect(select.style.height).toBe('48px');
    fixture.componentRef.setInput('controlHeight', null);
    fixture.detectChanges();
    expect(select.style.height).toBe('');
    expect(changes).not.toHaveBeenCalled();
  });

  describe('searchable selection', () => {
    beforeEach(() => {
      fixture.componentRef.setInput('searchable', true);
      fixture.componentRef.setInput('searchLabel', 'Search disciplines');
      fixture.componentRef.setInput('clearLabel', 'Clear');
      fixture.componentRef.setInput('emptyLabel', 'No disciplines found');
      fixture.componentRef.setInput('options', [
        { code: 'PL', label: 'Poland', count: 12 },
        { code: 'DE', label: 'Germany', count: 0 },
      ]);
      fixture.detectChanges();
      (fixture.nativeElement.querySelector('.multi-value-toggle') as HTMLButtonElement).click();
      fixture.detectChanges();
    });

    function search(text: string): void {
      const input = fixture.nativeElement.querySelector('input[type=search]') as HTMLInputElement;
      input.value = text;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    it('filters existing choices, preserves counts and supports multiple checked selections', () => {
      expect(fixture.nativeElement.querySelector('.multi-value-options').textContent).toContain(
        'Poland (12)',
      );
      search('GER');
      expect(fixture.nativeElement.querySelectorAll('input[type=checkbox]')).toHaveLength(1);
      expect(fixture.nativeElement.querySelector('.multi-value-options').textContent).toContain(
        'Germany (0)',
      );
      expect(changes).not.toHaveBeenCalled();
      (fixture.nativeElement.querySelector('input[type=checkbox]') as HTMLInputElement).click();
      expect(changes).toHaveBeenLastCalledWith(['DE']);
      fixture.componentRef.setInput('value', ['DE']);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('input[type=checkbox]').checked).toBe(true);
      search('pol');
      (fixture.nativeElement.querySelector('input[type=checkbox]') as HTMLInputElement).click();
      expect(changes).toHaveBeenLastCalledWith(['DE', 'PL']);
      fixture.componentRef.setInput('value', ['DE', 'PL']);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('ul').textContent).toContain('Germany');
      search('unknown discipline');
      expect(fixture.nativeElement.querySelector('[role=status]').textContent).toBe(
        'No disciplines found',
      );
      expect(component.value()).toEqual(['DE', 'PL']);
      changes.mockClear();
      component.toggle('UNKNOWN');
      expect(changes).not.toHaveBeenCalled();
    });

    it('does not submit on Enter and closes on Escape with focus returned to the toggle', () => {
      const search = fixture.nativeElement.querySelector('input[type=search]') as HTMLInputElement;
      const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
      search.dispatchEvent(enter);
      expect(enter.defaultPrevented).toBe(true);
      search.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.multi-value-panel')).toBeNull();
      expect(
        fixture.nativeElement.querySelector('.multi-value-toggle').getAttribute('aria-expanded'),
      ).toBe('false');
      expect(document.activeElement).toBe(
        fixture.nativeElement.querySelector('.multi-value-toggle'),
      );
      expect(changes).not.toHaveBeenCalled();
    });

    it('clears the selection without submitting and closes when clicking outside', () => {
      fixture.componentRef.setInput('value', ['PL', 'DE']);
      fixture.detectChanges();
      const clear = fixture.nativeElement.querySelector(
        '.multi-value-panel button',
      ) as HTMLButtonElement;
      expect(clear.type).toBe('button');
      clear.click();
      expect(changes).toHaveBeenCalledExactlyOnceWith([]);
      expect(component.isOpen()).toBe(true);
      document.body.click();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.multi-value-panel')).toBeNull();
    });
  });
});
