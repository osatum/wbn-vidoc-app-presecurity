import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { DashboardService } from '../../../core/backend';
import { provideRouter } from '@angular/router';
import { translocoTestingModule } from '../../../testing/transloco-testing-module';

import { AdminDashoboard } from './admin-dashoboard';

describe('AdminDashoboard', () => {
  let component: AdminDashoboard;
  let fixture: ComponentFixture<AdminDashoboard>;

  beforeAll(() => {
    Object.defineProperty(globalThis, 'ResizeObserver', {
      value: class ResizeObserver {
        observe(): void {}
        unobserve(): void {}
        disconnect(): void {}
      },
      writable: true,
    });
    Object.defineProperty(SVGElement.prototype, 'getBBox', {
      value: () => ({
        x: 0,
        y: 0,
        width: 0,
        height: 0,
      }),
      writable: true,
    });
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminDashoboard, translocoTestingModule],
      providers: [
        provideRouter([]),
        {
          provide: DashboardService,
          useValue: {
            getCardinality: () => of({}),
            getCharts: () => of({}),
            getLatestSessions: () => of([]),
            getNavigationAuditKpi: () => of({}),
          },
        },
      ],
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminDashoboard);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
