import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { translocoTestingModule } from '../../testing/transloco-testing-module';
import { PortalLayout } from './portal-layout';

describe('PortalLayout', () => {
  let component: PortalLayout;
  let fixture: ComponentFixture<PortalLayout>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PortalLayout, translocoTestingModule],
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

    fixture = TestBed.createComponent(PortalLayout);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
