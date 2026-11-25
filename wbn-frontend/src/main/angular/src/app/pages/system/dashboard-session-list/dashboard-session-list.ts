import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { catchError, map, of, startWith } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { DashboardService, DashboardSessionDto } from '../../../core/backend';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';
import { TranslocoPipe } from '@jsverse/transloco';

type DashboardSessionListState = {
  status: 'loading' | 'success' | 'error';
  data: DashboardSessionDto[];
  error: unknown | null;
};

const ACTOR_TYPE_LABEL_KEYS = {
  USER: 'page.system.dashboard-session-list.actor-type.user',
  INSTITUTION: 'page.system.dashboard-session-list.actor-type.institution',
} satisfies Record<DashboardSessionDto.ActorTypeEnum, string>;

const INSTITUTION_ROLE_LABEL_KEYS = {
  INSTITUTION_ADMIN: 'page.system.login-session-list.institution-role.admin',
  INSTITUTION_USER: 'page.system.login-session-list.institution-role.user',
  INSTITUTION_VIEWER: 'page.system.login-session-list.institution-role.viewer',
  INSTITUTION_PENDING: 'page.system.login-session-list.institution-role.pending',
  INSTITUTION_TO_CONFIRM: 'page.system.login-session-list.institution-role.to-confirm',
} satisfies Record<DashboardSessionDto.InstitutionRoleEnum, string>;

@Component({
  selector: 'app-dashboard-session-list',
  imports: [CommonModule, AlertData, LocalSpinner, TranslocoPipe],
  templateUrl: './dashboard-session-list.html',
  styleUrl: './dashboard-session-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardSessionList {
  private readonly dashboardService = inject(DashboardService);

  readonly state = toSignal(
    this.dashboardService.getLatestSessions().pipe(
      map(
        (data): DashboardSessionListState => ({
          status: 'success',
          data,
          error: null,
        }),
      ),
      startWith({
        status: 'loading',
        data: [],
        error: null,
      } satisfies DashboardSessionListState),
      catchError((error) =>
        of({
          status: 'error',
          data: [],
          error,
        } satisfies DashboardSessionListState),
      ),
    ),
    {
      initialValue: {
        status: 'loading',
        data: [],
        error: null,
      } satisfies DashboardSessionListState,
    },
  );

  readonly sessions = computed(() => this.state().data);
  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly hasError = computed(() => this.state().status === 'error');

  actorTypeLabelKey(value: DashboardSessionDto.ActorTypeEnum | undefined): string {
    return value ? (ACTOR_TYPE_LABEL_KEYS[value] ?? '') : '';
  }

  institutionRoleLabelKey(value: DashboardSessionDto.InstitutionRoleEnum | undefined): string {
    return value ? (INSTITUTION_ROLE_LABEL_KEYS[value] ?? '') : '';
  }

  trackBySession(_: number, session: DashboardSessionDto): number | string {
    return session.id ?? `${session.createdAt ?? ''}-${session.startedAt ?? ''}`;
  }
}
