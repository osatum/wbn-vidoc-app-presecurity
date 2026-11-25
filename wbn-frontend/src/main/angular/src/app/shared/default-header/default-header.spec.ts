import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { RedirectService } from '../../core/services/redirect-service';
import { EventService } from '../../theme/core/services/event.service';
import { translocoTestingModule } from '../../testing/transloco-testing-module';
import { DefaultHeader } from './default-header';

describe('DefaultHeader', () => {
  let component: DefaultHeader;
  let fixture: ComponentFixture<DefaultHeader>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DefaultHeader, translocoTestingModule],
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
        {
          provide: RedirectService,
          useValue: {
            redirectToDashboard: vi.fn(),
          },
        },
        {
          provide: EventService,
          useValue: {
            broadcast: vi.fn(),
          },
        },
      ],
    })
      .overrideComponent(DefaultHeader, { set: { imports: [], template: '' } })
      .compileComponents();

    fixture = TestBed.createComponent(DefaultHeader);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
