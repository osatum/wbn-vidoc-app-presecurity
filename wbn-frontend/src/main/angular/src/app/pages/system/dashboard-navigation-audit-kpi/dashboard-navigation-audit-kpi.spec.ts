import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { DashboardService } from '../../../core/backend';
import { translocoTestingModule } from '../../../testing/transloco-testing-module';
import { DashboardNavigationAuditKpi } from './dashboard-navigation-audit-kpi';

describe('DashboardNavigationAuditKpi', () => {
  let component: DashboardNavigationAuditKpi;
  let fixture: ComponentFixture<DashboardNavigationAuditKpi>;
  let dashboardService: {
    getNavigationAuditKpi: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    dashboardService = {
      getNavigationAuditKpi: vi.fn(() =>
        of({
          users: 3,
          institutions: 2,
          sessions: 5,
          events: 25,
        }),
      ),
    };

    await TestBed.configureTestingModule({
      imports: [DashboardNavigationAuditKpi, translocoTestingModule],
      providers: [
        provideRouter([]),
        { provide: DashboardService, useValue: dashboardService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardNavigationAuditKpi);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should expose navigation audit KPI metrics', () => {
    expect(component.metrics()).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          labelKey: 'page.system.dashboard-navigation-audit-kpi.metrics.users',
          counter: 3,
        }),
        expect.objectContaining({
          labelKey: 'page.system.dashboard-navigation-audit-kpi.metrics.institutions',
          counter: 2,
        }),
        expect.objectContaining({
          labelKey: 'page.system.dashboard-navigation-audit-kpi.metrics.sessions',
          counter: 5,
        }),
        expect.objectContaining({
          labelKey: 'page.system.dashboard-navigation-audit-kpi.metrics.events',
          counter: 25,
        }),
      ]),
    );
  });
});
