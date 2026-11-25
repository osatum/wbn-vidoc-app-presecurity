import { TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { OperatingProfileService } from '../../../core/services/operating-profile.service';
import { OperatingProfileSetting } from './operating-profile-setting';

class Loader implements TranslocoLoader {
  getTranslation() {
    return of({
      operatingProfile: {
        profile: { BASIC: 'Basic', ADVANCED: 'Advanced', BOTH: 'Both' },
        inherit: 'Inherit',
      },
    } as Translation);
  }
}

describe('OperatingProfileSetting', () => {
  let defaultProfile: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    defaultProfile = vi.fn(() => of('BOTH'));
    await TestBed.configureTestingModule({
      imports: [OperatingProfileSetting],
      providers: [
        provideTransloco({
          config: { availableLangs: ['pl'], defaultLang: 'pl', prodMode: true },
          loader: Loader,
        }),
        { provide: OperatingProfileService, useValue: { defaultProfile } },
      ],
    }).compileComponents();
  });

  it('shows configured/default/effective values and emits explicit inheritance', async () => {
    const fixture = TestBed.createComponent(OperatingProfileSetting);
    fixture.componentRef.setInput('editable', true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    expect(select.options.length).toBe(4);
    expect(select.value).toBe('');
    expect(fixture.componentInstance.effectiveProfile()).toBe('BOTH');
    const changed = vi.fn();
    fixture.componentInstance.valueChange.subscribe(changed);
    select.value = 'BASIC';
    select.dispatchEvent(new Event('change'));
    expect(changed).toHaveBeenLastCalledWith('BASIC');
    fixture.componentRef.setInput('value', 'BASIC');
    fixture.detectChanges();
    expect(fixture.componentInstance.effectiveProfile()).toBe('BASIC');
    select.value = '';
    select.dispatchEvent(new Event('change'));
    expect(changed).toHaveBeenLastCalledWith(null);
  });

  it('does not present an assumed default after failure and supports retry in readonly details', async () => {
    defaultProfile.mockReturnValueOnce(throwError(() => new Error('offline')));
    const fixture = TestBed.createComponent(OperatingProfileSetting);
    fixture.componentRef.setInput('value', 'ADVANCED');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('select')).toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="operating-profile-effective"]'),
    ).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
    fixture.nativeElement.querySelector('button').click();
    fixture.detectChanges();
    expect(fixture.componentInstance.defaultProfile()).toBe('BOTH');
    expect(fixture.componentInstance.effectiveProfile()).toBe('ADVANCED');
  });
});
