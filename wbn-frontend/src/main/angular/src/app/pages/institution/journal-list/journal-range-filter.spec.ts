import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { JournalRangeFilter } from './journal-range-filter';

describe('JournalRangeFilter', () => {
  let fixture: ComponentFixture<JournalRangeFilter>;
  let minimum: FormControl<string>;
  let maximum: FormControl<string>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [JournalRangeFilter] });
    minimum = new FormControl('', { nonNullable: true });
    maximum = new FormControl('', { nonNullable: true });
    fixture = TestBed.createComponent(JournalRangeFilter);
    for (const [key, value] of Object.entries({
      minimum,
      maximum,
      minimumId: 'lower',
      maximumId: 'upper',
      label: 'Range',
      minimumLabel: 'Minimum',
      maximumLabel: 'Maximum',
      unrestrictedLabel: 'Unrestricted',
      unavailableLabel: 'Unavailable',
    }))
      fixture.componentRef.setInput(key, value);
  });

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function move(key: string, upper = false): void {
    fixture.nativeElement
      .querySelector(upper ? '.noUi-handle-upper' : '.noUi-handle-lower')
      .dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
  }

  it('renders two labelled percentage handles and leaves untouched full-range controls blank', async () => {
    fixture.componentRef.setInput('unit', '%');
    await render();
    const handles = fixture.nativeElement.querySelectorAll('[role=slider]');
    expect(handles).toHaveLength(2);
    expect(handles[0].getAttribute('aria-label')).toBe('Range: Minimum');
    expect(handles[1].getAttribute('aria-label')).toBe('Range: Maximum');
    expect(handles[0].getAttribute('aria-valuenow')).toBe('0');
    expect(handles[1].getAttribute('aria-valuetext')).toBe('100%');
    expect(minimum.value).toBe('');
    expect(maximum.value).toBe('');
  });

  it('updates only the edited bound by keyboard, preserving partial ranges and true zero', async () => {
    await render();
    move('ArrowRight');
    expect(minimum.value).toBe('0.01');
    expect(maximum.value).toBe('');
    move('Home');
    expect(minimum.value).toBe('0');
    expect(maximum.value).toBe('');
    expect(minimum.dirty).toBe(true);
    expect(minimum.touched).toBe(true);
    move('ArrowLeft', true);
    expect(maximum.value).toBe('99.99');
  });

  it('keeps the slider editable and the error accessible when manual bounds are omitted', async () => {
    fixture.componentRef.setInput('showBounds', false);
    fixture.componentRef.setInput('discrete', true);
    fixture.componentRef.setInput('choices', [20, 40, 70, 200]);
    fixture.componentRef.setInput('invalid', true);
    fixture.componentRef.setInput('describedBy', 'range-error');
    await render();
    expect(fixture.nativeElement.querySelector('input, select')).toBeNull();
    const group = fixture.nativeElement.querySelector('fieldset') as HTMLFieldSetElement;
    group.focus();
    expect(document.activeElement).toBe(group);
    expect(group.getAttribute('aria-describedby')).toBe('range-error');
    expect(group.getAttribute('aria-invalid')).toBe('true');
    move('ArrowRight');
    expect(minimum.value).toBe('40');
    expect(maximum.value).toBe('');
  });

  it('selects only real nonuniform point classes and exposes real point values to assistive technology', async () => {
    fixture.componentRef.setInput('discrete', true);
    fixture.componentRef.setInput('choices', [200, 20, 40, 70, 40]);
    await render();
    move('ArrowRight');
    expect(minimum.value).toBe('40');
    move('ArrowRight');
    expect(minimum.value).toBe('70');
    expect(maximum.value).toBe('');
    const handle = fixture.nativeElement.querySelector('.noUi-handle-lower');
    expect(handle.getAttribute('aria-valuenow')).toBe('70');
    expect(handle.getAttribute('aria-valuemax')).toBe('200');
    const values = [...fixture.nativeElement.querySelectorAll('#lower option')].map(
      (option) => (option as HTMLOptionElement).value,
    );
    expect(values).toEqual(['', '20', '40', '70', '200']);
  });

  it.each([{ choices: [] }, { choices: [70] }])(
    'does not invent a slider interval for zero or one available point class: %s',
    async ({ choices }) => {
      fixture.componentRef.setInput('discrete', true);
      fixture.componentRef.setInput('choices', choices);
      await render();
      expect(fixture.nativeElement.querySelector('.noUi-target')).toBeNull();
      expect(minimum.value).toBe('');
      expect(maximum.value).toBe('');
    },
  );

  it('waits for point classes and does not change draft bounds after late metadata or replacement', async () => {
    fixture.componentRef.setInput('discrete', true);
    minimum.setValue('40');
    maximum.setValue('70');
    await render();
    fixture.componentRef.setInput('choices', [20, 40, 70, 200]);
    await render();
    expect(minimum.value).toBe('40');
    expect(maximum.value).toBe('70');
    fixture.componentRef.setInput('choices', [20, 100, 200]);
    await render();
    expect(minimum.value).toBe('40');
    expect(maximum.value).toBe('70');
    expect(fixture.nativeElement.querySelector('#lower option:disabled').textContent).toContain(
      '40',
    );
    fixture.componentRef.setInput('choices', []);
    await render();
    expect(fixture.nativeElement.querySelector('.noUi-target')).toBeNull();
  });

  it('does not change controls while disabled and restores handles without applying or normalizing raw data', async () => {
    fixture.componentRef.setInput('disabled', true);
    await render();
    move('ArrowRight');
    expect(minimum.value).toBe('');
    fixture.componentRef.setInput('disabled', false);
    minimum.setValue('25,5');
    await render();
    expect(minimum.value).toBe('25,5');
    move('ArrowRight');
    expect(minimum.value).toBe('25.51');
  });

  it.each([
    ['-1', '101'],
    ['90', '10'],
    ['invalid', ''],
    ['0', '0'],
    ['12,34567', ''],
  ])(
    'does not fix invalid, reversed, zero or high precision typed values %s/%s behind the user',
    async (lower, upper) => {
      await render();
      minimum.setValue(lower);
      maximum.setValue(upper);
      await render();
      expect(minimum.value).toBe(lower);
      expect(maximum.value).toBe(upper);
    },
  );

  it('synchronizes reset and validation labels without leaking stale slider listeners', async () => {
    fixture.componentRef.setInput('discrete', true);
    fixture.componentRef.setInput('choices', [20, 40, 70, 200]);
    await render();
    move('ArrowRight');
    minimum.reset('');
    maximum.reset('');
    fixture.componentRef.setInput('label', 'Updated range');
    fixture.componentRef.setInput('invalid', true);
    fixture.componentRef.setInput('describedBy', 'range-error');
    await render();
    const oldHandle = fixture.nativeElement.querySelector('.noUi-handle-lower');
    expect(oldHandle.getAttribute('aria-label')).toBe('Updated range: Minimum');
    expect(oldHandle.getAttribute('aria-describedby')).toBe('range-error');
    expect(oldHandle.getAttribute('aria-invalid')).toBe('true');
    expect(oldHandle.getAttribute('aria-valuenow')).toBe('20');
    fixture.destroy();
    minimum.setValue('70');
    expect(minimum.value).toBe('70');
  });
});
