import { TestBed } from '@angular/core/testing';
import { FilterCheckboxList } from './filter-checkbox-list';

describe('FilterCheckboxList', () => {
  function create() {
    const fixture = TestBed.createComponent(FilterCheckboxList);
    fixture.componentRef.setInput('label', 'Databases');
    fixture.componentRef.setInput('searchLabel', 'Search values');
    fixture.componentRef.setInput('options', [
      { code: 'z', label: 'Żurnal', count: 0 },
      { code: 'b', label: 'Beta', count: 2 },
      { code: 'a', label: 'Alpha', count: 1 },
    ]);
    fixture.componentRef.setInput('value', ['b']);
    fixture.detectChanges();
    return fixture;
  }

  it('sorts labels alphabetically and shows counts including zero', () => {
    const fixture = create();
    expect(fixture.componentInstance.filteredOptions().map((option) => option.code)).toEqual([
      'a',
      'b',
      'z',
    ]);
    expect(fixture.nativeElement.textContent.replace(/\s+/g, ' ')).toContain('Żurnal (0)');
    expect(fixture.nativeElement.querySelector('.overflow-auto')).toBeTruthy();
  });

  it('filters locally without changing selections and retains earlier choices', () => {
    const fixture = create();
    const changes = vi.fn();
    fixture.componentInstance.valueChange.subscribe(changes);
    fixture.componentInstance.search.set('alpha');
    fixture.detectChanges();
    expect(fixture.componentInstance.value()).toEqual(['b']);
    expect(changes).not.toHaveBeenCalled();
    fixture.nativeElement.querySelector('input[type=checkbox]').click();
    expect(changes).toHaveBeenCalledExactlyOnceWith(['b', 'a']);
  });

  it('can preserve domain order instead of sorting labels alphabetically', () => {
    const fixture = create();
    fixture.componentRef.setInput('sortAlphabetically', false);
    fixture.detectChanges();
    expect(fixture.componentInstance.filteredOptions().map((option) => option.code)).toEqual([
      'z',
      'b',
      'a',
    ]);
  });

  it('keeps selected database names below search when filtering the options and removes only the clicked value', () => {
    const fixture = create();
    fixture.componentRef.setInput('showSelection', true);
    fixture.componentRef.setInput('removeLabel', 'Remove');
    fixture.componentRef.setInput('value', ['b', 'z']);
    fixture.componentInstance.search.set('alpha');
    fixture.detectChanges();
    const search = fixture.nativeElement.querySelector('input[type=search]') as HTMLInputElement;
    const selected = search.nextElementSibling!;
    expect(selected.tagName).toBe('UL');
    const buttons = selected.querySelectorAll('button');
    expect([...buttons].map((button) => button.textContent?.trim())).toEqual([
      'Beta ×',
      'Żurnal ×',
    ]);
    expect(buttons[0].getAttribute('aria-label')).toBe('Remove: Beta');
    expect(buttons[0].type).toBe('button');
    expect(fixture.componentInstance.filteredOptions().map((option) => option.code)).toEqual(['a']);
    const changes = vi.fn();
    fixture.componentInstance.valueChange.subscribe(changes);
    buttons[0].click();
    expect(changes).toHaveBeenCalledExactlyOnceWith(['z']);
  });

  it('updates restored selections after dictionary loading and hides the summary when cleared', () => {
    const fixture = create();
    fixture.componentRef.setInput('showSelection', true);
    fixture.componentRef.setInput('options', []);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('ul').textContent).toContain('b');
    fixture.componentRef.setInput('options', [{ code: 'b', label: 'Restored database' }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('ul').textContent).toContain('Restored database');
    fixture.componentRef.setInput('value', []);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('ul')).toBeNull();
  });

  it('does not submit the journal form on Enter in its local search', () => {
    const fixture = create();
    const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
    fixture.nativeElement.querySelector('input[type=search]').dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
  });

  it('preserves unknown selected values during missing or refreshed dictionaries', () => {
    const fixture = create();
    fixture.componentRef.setInput('options', []);
    fixture.detectChanges();
    expect(fixture.componentInstance.filteredOptions()).toEqual([{ code: 'b', label: 'b' }]);
    fixture.componentRef.setInput('options', [{ code: 'b', label: 'Beta', count: 3 }]);
    fixture.detectChanges();
    expect(fixture.componentInstance.value()).toEqual(['b']);
    expect(fixture.componentInstance.filteredOptions()).toEqual([
      { code: 'b', label: 'Beta', count: 3 },
    ]);
  });

  it('removes only the toggled value without mutating the supplied array', () => {
    const fixture = create();
    const selected = ['b', 'z'];
    fixture.componentRef.setInput('value', selected);
    const changes = vi.fn();
    fixture.componentInstance.valueChange.subscribe(changes);
    fixture.componentInstance.toggle('b');
    expect(changes).toHaveBeenCalledExactlyOnceWith(['z']);
    expect(selected).toEqual(['b', 'z']);
  });
});
