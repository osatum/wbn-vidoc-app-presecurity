import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { catchError, finalize, map, of, startWith, switchMap } from 'rxjs';
import {
  DictionaryService,
  JournalListQuery,
  JournalSearchMetadata,
  JournalSearchCounts,
  RepositoryDictionaryView,
} from '../../../core/backend';
import { FilterCheckboxList } from '../../../shared/filter-checkbox-list/filter-checkbox-list';
import { MultiValueSelectOption } from '../../../shared/multi-value-select/multi-value-select';
import {
  ARTICLE_PUBLISHED_FREQUENCY,
  DIGITALIZATION_ARTICLE_PUBLISHED_LICENSE,
} from '../journal-ui-definitions';
import {
  NonFeeControls,
  NonFeeFeature,
  NonFeeSelection,
  nonFeeCriteriaFromQuery,
} from './journal-non-fee-criteria';

@Component({
  selector: 'app-journal-non-fee-filters',
  standalone: true,
  imports: [ReactiveFormsModule, TranslocoPipe, FilterCheckboxList],
  templateUrl: './journal-non-fee-filters.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalNonFeeFilters {
  private readonly dictionary = inject(DictionaryService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  readonly form = input.required<FormGroup<NonFeeControls>>();
  readonly criteria = toSignal(
    toObservable(this.form).pipe(
      switchMap((form) =>
        form.valueChanges.pipe(
          startWith(form.getRawValue()),
          map(() => form.getRawValue()),
        ),
      ),
    ),
    { initialValue: nonFeeCriteriaFromQuery() },
  );
  readonly metadata = input<JournalSearchMetadata | null>(null);
  readonly counts = input<JournalSearchCounts | null>(null);
  readonly hasValidationError = input(false);
  readonly section = input.required<'indexation' | 'process' | 'standards'>();
  readonly locale = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  readonly repositories = signal<RepositoryDictionaryView[]>([]);
  readonly isRepositoryLoading = signal(false);
  readonly hasRepositoryError = signal(false);
  readonly editorialFeatures: NonFeeFeature[] = [
    'doiOnly',
    'editorialSystemOnly',
    'antiPlagiarismOnly',
    'newsletterOnly',
    'socialMediaOnly',
  ];
  readonly repositoryOptions = computed(() =>
    this.repositories()
      .filter(
        (repository): repository is RepositoryDictionaryView & { id: number; name: string } =>
          repository.id !== undefined && !!repository.name,
      )
      .map((repository) => ({
        code: String(repository.id),
        label: repository.name,
        count:
          this.counts()?.repositoryCounts?.[String(repository.id)] ??
          (this.counts()?.repositoryCounts ? 0 : undefined),
      })),
  );
  readonly licenseOptions = computed(() =>
    this.enumOptions(this.counts()?.licenseCounts, DIGITALIZATION_ARTICLE_PUBLISHED_LICENSE,
      Object.keys(this.metadata()?.licenseCounts ?? DIGITALIZATION_ARTICLE_PUBLISHED_LICENSE)),
  );
  readonly frequencyOptions = computed(() =>
    this.enumOptions(this.counts()?.frequencyCounts, ARTICLE_PUBLISHED_FREQUENCY, [
      'k',
      'e',
      'c',
      'm',
      'b',
      'q',
      't',
      'f',
      'a',
      'g',
    ] satisfies JournalListQuery.FrequencyCodesEnum[]),
  );
  readonly reviewModelOptions = computed(() =>
    this.enumOptions(
      this.counts()?.reviewModelCounts,
      {
        DOUBLE_BLIND_REVIEW: 'page.institution.journalList.reviewModelLabels.DOUBLE_BLIND_REVIEW',
        SINGLE_BLIND_REVIEW: 'page.institution.journalList.reviewModelLabels.SINGLE_BLIND_REVIEW',
        OPEN_PEER_REVIEW: 'page.institution.journalList.reviewModelLabels.OPEN_PEER_REVIEW',
      } satisfies Record<JournalListQuery.ReviewModelsEnum, string>,
      ['DOUBLE_BLIND_REVIEW', 'SINGLE_BLIND_REVIEW', 'OPEN_PEER_REVIEW'],
    ),
  );
  readonly accessOptions = computed(() =>
    this.enumOptions(this.counts()?.accessModelCounts, {
      FREE_ARTICLES: 'page.institution.journalList.accessModelOa',
      PAID_ARTICLES: 'page.institution.journalList.accessModelHybrid',
      ALL_ARTICLES_PAID: 'page.institution.journalList.accessModelClosed',
    }, Object.keys(this.metadata()?.accessModelCounts ?? {
      FREE_ARTICLES: '', PAID_ARTICLES: '', ALL_ARTICLES_PAID: '',
    })),
  );

  constructor() {
    effect(() => {
      if (this.section() === 'indexation') untracked(() => this.loadRepositories());
    });
  }

  loadRepositories(): void {
    if (this.isRepositoryLoading()) return;
    this.isRepositoryLoading.set(true);
    this.hasRepositoryError.set(false);
    this.dictionary
      .findRepositories('body', false, { transferCache: false })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        catchError(() => {
          this.hasRepositoryError.set(true);
          return of(null);
        }),
        finalize(() => this.isRepositoryLoading.set(false)),
      )
      .subscribe((repositories) => {
        if (repositories) this.repositories.set(repositories);
      });
  }

  setSelection(field: NonFeeSelection, values: string[]): void {
    this.form().controls[field].setValue([...values]);
  }

  featureCount(feature: NonFeeFeature): number | undefined {
    return this.counts()?.featureCounts?.[feature];
  }

  private enumOptions(
    counts: Record<string, number> | undefined,
    labels: Record<string, string>,
    codes: readonly string[] = Object.keys(counts ?? {}),
  ): MultiValueSelectOption[] {
    this.locale();
    return codes.map((code) => {
      const count = counts ? (counts[code] ?? 0) : undefined;
      const key = labels[code] ?? code;
      return { code, count, label: key.includes('.') ? this.transloco.translate(key) : key };
    });
  }
}
