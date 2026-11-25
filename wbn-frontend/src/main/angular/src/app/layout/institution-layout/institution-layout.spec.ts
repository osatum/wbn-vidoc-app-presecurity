import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { translocoTestingModule } from '../../testing/transloco-testing-module';
import { InstitutionLayout } from './institution-layout';

describe('InstitutionLayout', () => {
  let component: InstitutionLayout;
  let fixture: ComponentFixture<InstitutionLayout>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InstitutionLayout, translocoTestingModule],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            userProfile$: of(null),
            resetPendingInstitution: vi.fn(),
            logout: vi.fn(),
            selectInstitution: vi.fn(() => of({})),
            getUserProfile: vi.fn(() => of(null)),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InstitutionLayout);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
