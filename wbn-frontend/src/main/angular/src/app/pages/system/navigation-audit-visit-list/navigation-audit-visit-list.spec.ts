import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule, TranslocoService } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { NavigationAuditVisitList } from './navigation-audit-visit-list';
import { NavigationAuditVisitListService } from '../../../core/services/navigation-audit-visits.service';
import type { NavigationAuditVisitPageResult } from '../../../core/backend';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';

describe('NavigationAuditVisitList', () => {
  let fixture: ComponentFixture<NavigationAuditVisitList>;
  let component: NavigationAuditVisitList;
  let findVisits: ReturnType<typeof vi.fn>;
  const response: NavigationAuditVisitPageResult = {
    data: [
      {
        id: 1,
        actorType: 'USER',
        userId: 2,
        userEmail: 'ada@example.test',
        userFirstName: 'Ada',
        status: 'OPEN',
        startedAt: '2026-10-03T10:00:00Z',
        lastActivityAt: '2026-10-03T10:20:00Z',
        durationSeconds: 1200,
      },
    ],
    totalElements: 20,
    totalPages: 2,
    size: 10,
    timeZone: 'America/New_York',
  };
  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    findVisits = vi.fn(() => of(response));
    await TestBed.configureTestingModule({
      imports: [
        NavigationAuditVisitList,
        TranslocoTestingModule.forRoot({
          langs: { pl, en },
          translocoConfig: { availableLangs: ['pl', 'en'], defaultLang: 'pl', prodMode: true },
          preloadLangs: true,
        }),
      ],
      providers: [{ provide: NavigationAuditVisitListService, useValue: { findVisits } }],
    }).compileComponents();
    fixture = TestBed.createComponent(NavigationAuditVisitList);
    component = fixture.componentInstance;
  });

  it('does not load until enabled and retains the page when hidden and shown again', async () => {
    fixture.componentRef.setInput('enabled', false);
    await settle();
    expect(findVisits).not.toHaveBeenCalled();
    fixture.componentRef.setInput('enabled', true);
    await settle();
    expect(findVisits).toHaveBeenCalledOnce();
    component.nextPage();
    await settle();
    expect(findVisits).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1, first: 10, size: 10 }),
    );
    fixture.componentRef.setInput('enabled', false);
    await settle();
    fixture.componentRef.setInput('enabled', true);
    await settle();
    expect(findVisits).toHaveBeenCalledTimes(2);
    expect(component.page()).toBe(1);
  });

  it('filters by calendar dates, trimmed identity, status and reason and resets pagination', async () => {
    await settle();
    component.nextPage();
    await settle();
    component.filterForm.setValue({
      dateFrom: '2026-10-03',
      dateTo: '',
      institution: ' Research ',
      user: ' ada@example.test ',
      status: 'CLOSED',
      endReason: 'INACTIVITY',
    });
    component.applyFilters();
    await settle();
    expect(findVisits).toHaveBeenLastCalledWith({
      page: 0,
      first: 0,
      size: 10,
      dateFrom: '2026-10-03',
      dateTo: undefined,
      institution: 'Research',
      user: 'ada@example.test',
      status: 'CLOSED',
      endReason: 'INACTIVITY',
    });
    component.changePageSizeValue(25);
    await settle();
    expect(findVisits).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, size: 25 }));
    component.resetFilters();
    await settle();
    expect(findVisits).toHaveBeenLastCalledWith(
      expect.objectContaining({ dateFrom: undefined, status: undefined, user: undefined }),
    );
  });

  it('rejects reversed dates and oversized filters before requesting', async () => {
    await settle();
    component.filterForm.patchValue({ dateFrom: '2026-10-04', dateTo: '2026-10-03' });
    component.applyFilters();
    await settle();
    expect(component.filterForm.hasError('dateRange')).toBe(true);
    expect(findVisits).toHaveBeenCalledOnce();
    component.filterForm.patchValue({ dateTo: '', user: 'x'.repeat(201) });
    component.applyFilters();
    await settle();
    expect(findVisits).toHaveBeenCalledOnce();
  });

  it('refreshes explicitly without resetting filters or page and does not read while hidden', async () => {
    await settle();
    component.nextPage();
    await settle();
    component.refresh();
    await settle();
    expect(findVisits).toHaveBeenCalledTimes(3);
    expect(component.page()).toBe(1);
    fixture.componentRef.setInput('enabled', false);
    await settle();
    component.refresh();
    await settle();
    expect(findVisits).toHaveBeenCalledTimes(3);
  });

  it('shows errors and permits retry without losing filters', async () => {
    findVisits.mockReturnValue(throwError(() => new Error('Network failed')));
    await settle();
    expect(component.hasError()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain(
      pl.page.system['navigation-audit-visit-list']['load-error'],
    );
    findVisits.mockReturnValue(of(response));
    component.refresh();
    await settle();
    expect(component.hasError()).toBe(false);
    expect(findVisits).toHaveBeenCalledTimes(2);
  });

  it.each(['pl', 'en'])(
    'renders labels, server-zone dates, duration and status in %s',
    async (language) => {
      TestBed.inject(TranslocoService).setActiveLang(language);
      await settle();
      const view = fixture.nativeElement as HTMLElement;
      expect(view.textContent).toContain('06:00:00');
      expect(view.textContent).toContain('0:20:00');
      expect(view.textContent).toContain('America/New_York');
      expect(view.textContent).toContain(language === 'en' ? 'Open' : 'Otwarta');
      expect(view.textContent).toContain('ada@example.test');
      expect(view.querySelectorAll('thead th')).toHaveLength(8);
      for (const field of ['date-from', 'date-to', 'institution', 'user', 'status', 'reason']) {
        expect(view.querySelector(`label[for="visit-${field}"]`)).not.toBeNull();
      }
    },
  );

  it('formats long and zero durations without a ticking timer', async () => {
    await settle();
    expect(component.formatDuration(90061)).toBe('25:01:01');
    expect(component.formatDuration(0)).toBe('0:00:00');
    expect(component.items()[0].durationSeconds).toBe(1200);
    expect(findVisits).toHaveBeenCalledOnce();
  });

  it('handles institutional and anonymized actors, missing relations and unknown reasons', async () => {
    findVisits.mockReturnValue(
      of({
        ...response,
        data: [
          {
            id: 2,
            actorType: 'INSTITUTION',
            status: 'CLOSED',
            endReason: 'LOGOUT',
            durationSeconds: 0,
          },
          {
            id: 3,
            actorType: 'USER',
            status: 'CLOSED',
            endReason: 'FUTURE_REASON',
            durationSeconds: 1200,
          },
        ],
      }),
    );
    await settle();
    const view = fixture.nativeElement as HTMLElement;
    expect(view.textContent).toContain('Dostęp instytucjonalny');
    expect(view.textContent).toContain('Użytkownik zanonimizowany');
    expect(view.textContent).toContain('Wylogowanie');
    expect(view.textContent).toContain('FUTURE_REASON');
    expect(view.textContent).toContain('—');
  });

  it('shows the empty-state message', async () => {
    findVisits.mockReturnValue(of({ ...response, data: [], totalElements: 0, totalPages: 0 }));
    await settle();
    expect(fixture.nativeElement.textContent).toContain('Brak wizyt dla wybranych kryteriów.');
    expect(component.canGoNext()).toBe(false);
  });
});
