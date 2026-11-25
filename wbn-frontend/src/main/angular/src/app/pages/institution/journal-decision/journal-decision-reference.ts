import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';
import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { DecisionGroup, DecisionValue } from '../../../core/services/journal-decision-annual';
import { JournalNameService } from '../../../core/services/journal-name-service';
import { formatDecisionNumber } from './journal-decision-presentation';

import { JournalIcvCard } from '../../shared/journal-icv-card/journal-icv-card';

type ListKey = 'indexing' | 'wos' | 'scopus';
interface ReferenceList {
  key: ListKey;
  initial: string[];
  additional: string[];
  visible: string[];
  moreCount: number;
  expanded: boolean;
  partial: boolean;
}

@Component({
  selector: 'app-journal-decision-reference',
  imports: [CommonModule, TranslocoPipe, NgbAccordionModule, JournalIcvCard],
  templateUrl: './journal-decision-reference.html',
  styleUrls: [
    '../../../shared/keyboard-focus.css',
    '../../../shared/journal-disclosure-accordion.css',
  ],
  styles: `
    :host {
      display: grid;
      gap: 1rem;
      min-width: 0;
    }
    .card {
      margin-bottom: 0;
      min-width: 0;
      overflow-wrap: anywhere;
    }
    .card-body {
      color: var(--vz-emphasis-color);
    }
    li + li {
      margin-top: 0.25rem;
    }
    .profile-label {
      font-size: inherit !important;
    }
    :host .decision-indexing-more .accordion-body {
      padding: 0.25rem 0 0;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalDecisionReference {
  readonly card = input.required<JournalDecisionView>();
  readonly expanded = output<'E03' | 'E04' | 'E05' | 'E06'>();
  readonly historyOpen = signal(false);
  readonly mniswOpen = signal(false);
  private readonly expandedLists = signal<ReadonlySet<ListKey>>(new Set());
  private readonly names = inject(JournalNameService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  private previousContext?: string;

  readonly annualAvailable = computed(() => this.card().annualStatus === 'AVAILABLE');
  readonly mainGroup = computed(() => {
    this.language();
    const group = this.card().annual?.profile.mainGroup;
    return group?.status === 'AVAILABLE' && group.value
      ? this.categoryName(group.value)
      : undefined;
  });
  readonly indexing = computed(() =>
    this.list('indexing', this.card().annual?.indexing, 5, (item) => item.name),
  );
  readonly mniswDisciplines = computed(() => {
    this.language();
    return (this.card().mnisw?.disciplines ?? []).map(
      (value) => this.names.getCategoryName(value)?.trim() || '—',
    );
  });
  readonly categories = computed(() => {
    this.language();
    const profile = this.card().annual?.profile;
    const label = (item: {
      namePl: string | null;
      nameEn: string | null;
      nameRu: string | null;
      code: string | null;
    }) => this.categoryName(item)?.trim() || item.code || '—';
    return [
      this.list('wos', profile?.wosCategories, 3, label, (item) => item.main === true),
      this.list('scopus', profile?.scopusCategories, 3, label, (item) => item.main === true),
    ];
  });

  constructor() {
    effect(() => {
      const context = `${this.card().identity.id}:${this.card().surveyYear}`;
      if (context !== this.previousContext) {
        this.historyOpen.set(false);
        this.mniswOpen.set(false);
        this.expandedLists.set(new Set());
        this.previousContext = context;
      }
    });
  }

  readonly formatNumber = (value: number | null | undefined) =>
    formatDecisionNumber(value, this.language());

  toggleList(key: ListKey): void {
    this.setListOpen(key, !this.expandedLists().has(key));
  }

  setListOpen(key: ListKey, opening: boolean): void {
    if (this.expandedLists().has(key) === opening) return;
    this.expandedLists.update((current) => {
      const next = new Set(current);
      if (opening) next.add(key);
      else next.delete(key);
      return next;
    });
    if (opening) {
      const actions = { indexing: 'E04', wos: 'E05', scopus: 'E06' } as const;
      this.expanded.emit(actions[key]);
    }
  }

  setHistoryOpen(open: boolean): void {
    if (this.historyOpen() === open) return;
    this.historyOpen.set(open);
    if (open) this.expanded.emit('E03');
  }

  private categoryName(
    value: Pick<DecisionGroup, 'namePl' | 'nameEn' | 'nameRu'>,
  ): string | undefined {
    return this.names.getCategoryName({
      namePl: value.namePl ?? undefined,
      nameEn: value.nameEn ?? undefined,
      nameRu: value.nameRu ?? undefined,
    });
  }

  private list<T>(
    key: ListKey,
    source: DecisionValue<T[]> | undefined,
    limit: number,
    label: (item: T) => string,
    isMain: (item: T) => boolean = () => false,
  ): ReferenceList {
    const collator = new Intl.Collator(this.language(), { sensitivity: 'base' });
    const items = (source?.value ?? [])
      .map((item) => ({ label: label(item), main: isMain(item) }))
      .sort((a, b) => Number(b.main) - Number(a.main) || collator.compare(a.label, b.label))
      .map((item) => item.label);
    const expanded = this.expandedLists().has(key);
    const initial = items.slice(0, limit);
    const additional = items.slice(limit);
    return {
      key,
      initial,
      additional,
      visible: expanded ? items : initial,
      moreCount: additional.length,
      expanded,
      partial: source?.status !== 'AVAILABLE' && items.length > 0,
    };
  }
}
