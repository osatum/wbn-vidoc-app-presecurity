import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';

import { InstitutionService } from '../../../core/backend';
import { ToastService } from '../../../core/services/toast-service';
import { InstitutionForm } from './institution-form';

describe('InstitutionForm', () => {
  let component: InstitutionForm;
  let fixture: ComponentFixture<InstitutionForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InstitutionForm],
      providers: [
        provideRouter([]),
        {
          provide: InstitutionService,
          useValue: {
            createInstitution: vi.fn(() => of({})),
            updateInstitution: vi.fn(() => of({})),
            findInstitution: vi.fn(() => of({})),
          },
        },
        { provide: ToastService, useValue: { show: vi.fn() } },
        { provide: TranslocoService, useValue: { translate: vi.fn((key: string) => key) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InstitutionForm);
    component = fixture.componentInstance;
  });

  it('accepts an IPv6 CIDR rule', () => {
    component.ipRuleDraft.setValue({
      addressFamily: 'IPV6',
      ruleType: 'CIDR',
      ruleRaw: '2001:db8:1234::/48',
      priority: 0,
    });

    expect(component.ipRuleDraft.valid).toBe(true);
  });

  it('sends the selected profile and clears it when changing to a system operator', () => {
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    component.form.patchValue({ name: 'Unit', operatingProfile: 'BASIC' });
    component.save();
    expect(TestBed.inject(InstitutionService).createInstitution).toHaveBeenLastCalledWith(expect.objectContaining({ operatingProfile: 'BASIC' }));
    component.form.controls.institutionType.setValue('SYSTEM_OPERATOR');
    expect(component.form.controls.operatingProfile.value).toBeNull();
    component.save();
    expect(TestBed.inject(InstitutionService).createInstitution).toHaveBeenLastCalledWith(expect.objectContaining({ operatingProfile: null }));
  });

  it('sends null rather than copying the global profile when inheriting', () => {
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    component.form.patchValue({ name: 'Inherited unit', operatingProfile: null });
    component.save();
    expect(TestBed.inject(InstitutionService).createInstitution).toHaveBeenLastCalledWith(expect.objectContaining({ operatingProfile: null }));
  });

  it('rejects an IPv6 range rule', () => {
    component.ipRuleDraft.setValue({
      addressFamily: 'IPV6',
      ruleType: 'RANGE',
      ruleRaw: '2001:db8::1-2001:db8::2',
      priority: 0,
    });

    expect(component.ipRuleDraft.valid).toBe(false);
  });

  it('rejects an IPv6 CIDR prefix above 128', () => {
    component.ipRuleDraft.setValue({
      addressFamily: 'IPV6',
      ruleType: 'CIDR',
      ruleRaw: '2001:db8::/129',
      priority: 0,
    });

    expect(component.ipRuleDraft.valid).toBe(false);
  });
});
