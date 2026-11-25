import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import {PagedListBase} from '../../../shared/paged-list-base/paged-list-base';
import {
  InstitutionListQuery,
  InstitutionListViewDto,
  InstitutionStateChangeResultDto,
  InstitutionService,
  PageResult,
} from '../../../core/backend';
import {Router, RouterLink} from '@angular/router';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { ListPaging } from '../../../shared/list-paging/list-paging';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { EntityStateChangeForm } from '../../../core/backend/model/entityStateChangeForm';
import { catchError, finalize, of } from 'rxjs';
import { ToastService } from '../../../core/services/toast-service';
import {
  SYSTEM_ENTITY_STATE_BADGE_CLASSES,
  SYSTEM_INSTITUTION_ENTITY_STATE_LABELS,
  SYSTEM_INSTITUTION_TYPE_LABELS,
} from '../../shared/system-ui-definitions';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';
import { InstitutionListQueryStateService } from '../../../core/services/institution-list-query-state.service';

type InstitutionFilters = {
  name: string;
  institutionType: '' | InstitutionListQuery.InstitutionTypeEnum;
};

const EMPTY_INSTITUTION_PAGE: PageResult = {
  data: [],
  totalElements: 0,
  totalPages: 0,
};

@Component({
  selector: 'app-institution-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    AlertData,
    ListPaging,
    TranslocoPipe,
    LocalSpinner,
  ],
  templateUrl: './institution-list.html',
  styleUrl: './institution-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstitutionList extends PagedListBase<
  InstitutionListQuery,
  InstitutionListViewDto,
  PageResult
> {
  private readonly institutionService = inject(InstitutionService);
  private readonly injector = inject(Injector);
  private readonly fb = inject(FormBuilder);
  private readonly toastService = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly router = inject(Router);
  private readonly institutionListQueryState = inject(InstitutionListQueryStateService);

  readonly stateChangePendingIds = signal<ReadonlySet<number>>(new Set<number>());
  private readonly isQueryInitialized = signal(false);

  readonly institutionTypes = [
    {
      value: InstitutionListQuery.InstitutionTypeEnum.SystemOperator,
      labelKey: 'page.system.institution-list.institution-type.system-operator',
    },
    {
      value: InstitutionListQuery.InstitutionTypeEnum.ResearchUnit,
      labelKey: 'page.system.institution-list.institution-type.research-unit',
    },
  ] satisfies ReadonlyArray<{
    value: InstitutionListQuery.InstitutionTypeEnum;
    labelKey: string;
  }>;

  readonly filterForm = this.fb.nonNullable.group({
    name: [''],
    institutionType: ['' as InstitutionFilters['institutionType']],
  });

  private readonly filters = signal<InstitutionFilters>(this.filterForm.getRawValue());

  readonly query = computed<InstitutionListQuery>(() => {
    const filters = this.filters();

    return {
      ...this.buildPagingQuery(),
      name: filters.name.trim() || undefined,
      institutionType: filters.institutionType || undefined,
    };
  });

  constructor() {
    super();

    this.initializePagedList({
      query: this.query,
      emptyPage: EMPTY_INSTITUTION_PAGE,
      injector: this.injector,
      enabled: this.isQueryInitialized,
      loader: (query) => this.institutionService.searchInstitutions(query),
    });
    this.persistQueryState();
    this.loadInitialQuery();
  }

  private loadInitialQuery(): void {
    this.institutionListQueryState
      .getInitialQuery()
      .pipe(catchError(() => of(this.defaultInstitutionListQuery())))
      .subscribe((query) => {
        this.applyInitialQuery(query);
        this.isQueryInitialized.set(true);
      });
  }

  private persistQueryState(): void {
    effect(() => {
      if (!this.isQueryInitialized()) {
        return;
      }

      this.institutionListQueryState.saveQuery(this.query());
    });
  }

  private applyInitialQuery(query: InstitutionListQuery): void {
    const size = this.positiveInteger(query.size);

    if (size !== undefined) {
      this.changePageSizeValue(size);
    }

    this.page.set(this.nonNegativeInteger(query.page) ?? 0);
    this.filterForm.reset(this.filtersFromQuery(query));
    this.filters.set(this.filterForm.getRawValue());
  }

  private filtersFromQuery(query: InstitutionListQuery): InstitutionFilters {
    return {
      name: this.textFilterValue(query.name),
      institutionType: this.institutionTypeFromQuery(query.institutionType),
    };
  }

  private defaultInstitutionListQuery(): InstitutionListQuery {
    return {
      page: 0,
      size: 10,
      first: 0,
    };
  }

  private institutionTypeFromQuery(value: unknown): InstitutionFilters['institutionType'] {
    if (
      value &&
      this.institutionTypes.some((institutionType) => institutionType.value === value)
    ) {
      return value as InstitutionListQuery.InstitutionTypeEnum;
    }

    return '';
  }

  private textFilterValue(value: unknown): string {
    return typeof value === 'string' ? value : '';
  }

  private positiveInteger(value: unknown): number | undefined {
    const parsed = this.integerValue(value);

    if (parsed === undefined || parsed <= 0) {
      return undefined;
    }

    return parsed;
  }

  private nonNegativeInteger(value: unknown): number | undefined {
    const parsed = this.integerValue(value);

    if (parsed === undefined || parsed < 0) {
      return undefined;
    }

    return parsed;
  }

  private integerValue(value: unknown): number | undefined {
    if (typeof value === 'number') {
      return Number.isInteger(value) ? value : undefined;
    }

    if (typeof value !== 'string') {
      return undefined;
    }

    const trimmed = value.trim();

    if (!trimmed) {
      return undefined;
    }

    const parsed = Number(trimmed);
    return Number.isInteger(parsed) ? parsed : undefined;
  }

  applyFilters(): void {
    this.resetPaging();
    this.filters.set(this.filterForm.getRawValue());
  }

  resetFilters(): void {
    this.filterForm.reset({
      name: '',
      institutionType: '',
    });

    this.resetPaging();
    this.filters.set(this.filterForm.getRawValue());
  }

  trackByInstitution(_: number, institution: InstitutionListViewDto): string {
    return `${institution.name ?? ''}-${institution.institutionType ?? ''}`;
  }

  institutionTypeLabelKey(
    institutionType: InstitutionListViewDto.InstitutionTypeEnum | undefined,
  ): string {
    return institutionType ? (SYSTEM_INSTITUTION_TYPE_LABELS[institutionType] ?? '-') : '-';
  }

  stateLabelKey(state: InstitutionListViewDto.StateEnum | undefined): string {
    return state ? (SYSTEM_INSTITUTION_ENTITY_STATE_LABELS[state] ?? '-') : '-';
  }

  stateBadgeClass(state: InstitutionListViewDto.StateEnum | undefined): string {
    return state
      ? (SYSTEM_ENTITY_STATE_BADGE_CLASSES[state] ?? 'badge bg-light text-muted')
      : 'badge bg-light text-muted';
  }

  stateActionLabelKey(state: InstitutionListViewDto.StateEnum | undefined): string {
    return state === InstitutionListViewDto.StateEnum.Active
      ? 'page.system.common.deactivate'
      : 'page.system.common.activate';
  }

  stateActionIcon(state: InstitutionListViewDto.StateEnum | undefined): string {
    return state === InstitutionListViewDto.StateEnum.Active
      ? 'ri-close-circle-line'
      : 'ri-checkbox-circle-line';
  }

  stateActionButtonClass(state: InstitutionListViewDto.StateEnum | undefined): string {
    return state === InstitutionListViewDto.StateEnum.Active
      ? 'btn btn-soft-warning btn-label mb-3'
      : 'btn btn-soft-success btn-label mb-3';
  }

  isStateChangePending(id: number | undefined): boolean {
    return id !== undefined && this.stateChangePendingIds().has(id);
  }

  changeState(institution: InstitutionListViewDto): void {
    const id = institution.id;
    const state = this.nextState(institution.state);

    if (id === undefined || state === undefined || this.isStateChangePending(id)) {
      return;
    }

    this.markStateChangePending(id, true);

    this.institutionService
      .changeInstitutionState(id, { state })
      .pipe(finalize(() => this.markStateChangePending(id, false)))
      .subscribe({
        next: (result) => this.handleStateChangeResult(id, result),
        error: () => {
          this.toastService.show(
            this.transloco.translate('page.system.common.state-change-error'),
            {
              classname: 'bg-danger text-white',
              delay: 7000,
            },
          );
        },
      });
  }

  private handleStateChangeResult(
    id: number,
    result: InstitutionStateChangeResultDto | undefined,
  ): void {
    if (result?.status === InstitutionStateChangeResultDto.StatusEnum.MembershipSelectionRequired) {
      this.router.navigate(['/system/admin/institutions', id, 'activation']);
      return;
    }

    this.toastService.show(this.transloco.translate('page.system.common.state-change-success'), {
      classname: 'bg-success text-white',
      delay: 5000,
    });
    this.reloadPage();
  }

  private nextState(
    state: InstitutionListViewDto.StateEnum | undefined,
  ): EntityStateChangeForm.StateEnum | undefined {
    if (state === InstitutionListViewDto.StateEnum.Active) {
      return EntityStateChangeForm.StateEnum.Inactive;
    }

    if (state === InstitutionListViewDto.StateEnum.Inactive) {
      return EntityStateChangeForm.StateEnum.Active;
    }

    return undefined;
  }

  private markStateChangePending(id: number, pending: boolean): void {
    this.stateChangePendingIds.update((ids) => {
      const next = new Set(ids);

      if (pending) {
        next.add(id);
      } else {
        next.delete(id);
      }

      return next;
    });
  }
}
