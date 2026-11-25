import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { InstitutionIpRuleDto, InstitutionService } from '../../../core/backend';
import { InstitutionDeletionService } from '../../../core/services/institution-deletion.service';
import { InstitutionDetails } from './institution-details';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('InstitutionDetails', () => {
  let component: InstitutionDetails;
  let fixture: ComponentFixture<InstitutionDetails>;

  beforeEach(async () => {
    const institutionDeletionService = {
      createConfirmation: vi.fn(() =>
        of({
          confirmationId: '9a21f5a0-5d0e-49e8-9f96-0a540c21e2de',
          confirmationCode: 'AB34CD56',
          expiresAt: '2026-08-04T20:05:00Z',
        }),
      ),
      deleteInstitution: vi.fn(() => of(undefined)),
    };

    await TestBed.configureTestingModule({
      imports: [InstitutionDetails],
      providers: [
        provideRouter([]),
        provideTransloco({
          config: {
            availableLangs: ['pl'],
            defaultLang: 'pl',
            prodMode: true,
          },
          loader: TestingTranslocoLoader,
        }),
        {
          provide: InstitutionService,
          useValue: {
            findInstitution: () => of({ name: 'Instytucja testowa' }),
          },
        },
        { provide: InstitutionDeletionService, useValue: institutionDeletionService },
        { provide: NgbModal, useValue: { open: vi.fn() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InstitutionDetails);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('formats the address family before the IP rule type', () => {
    expect(component.ipAddressFamilyLabel(InstitutionIpRuleDto.AddressFamilyEnum.Ipv6)).toBe('IPv6');
    expect(component.ipRuleTypeLabelKey(InstitutionIpRuleDto.RuleTypeEnum.Cidr)).toBe(
      'page.system.institution-form.options.cidr',
    );
  });

  it('normalizes the confirmation code and enables a matching code', () => {
    component.deletionConfirmation.set({
      confirmationId: '9a21f5a0-5d0e-49e8-9f96-0a540c21e2de',
      confirmationCode: 'AB34CD56',
      expiresAt: '2026-08-04T20:05:00Z',
    });
    const input = document.createElement('input');
    input.value = 'ab-34 cd56x';
    const event = new Event('input');
    Object.defineProperty(event, 'target', { value: input });

    component.onDeletionConfirmationCodeInput(event);

    expect(component.deletionConfirmationCode()).toBe('AB34CD56');
    expect(component.deletionConfirmationCodeMatches()).toBe(true);
  });
});
