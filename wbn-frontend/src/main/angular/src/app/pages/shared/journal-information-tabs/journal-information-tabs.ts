import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { CategoryView, InstitutionView, JournalView } from '../../../core/backend';
import { JournalNameService } from '../../../core/services/journal-name-service';
import { InstitutionData } from '../institution-data/institution-data';
import { PersonData } from '../person-data/person-data';

type InformationFields =
  | 'id'
  | 'titleOriginal'
  | 'titleEnglish'
  | 'titleShort'
  | 'country'
  | 'kind'
  | 'printedVersion'
  | 'electronicVersion'
  | 'paidArticle'
  | 'descriptions'
  | 'scientificCategory'
  | 'scientificCategories'
  | 'trCategory'
  | 'trCategories'
  | 'ajscCategory'
  | 'ajscCategories'
  | 'editorInChiefs'
  | 'deputyEditorInChiefs'
  | 'editorialSecretaries'
  | 'editors'
  | 'affiliationName';

export type JournalInformation = { [K in InformationFields]?: JournalView[K] | null };

@Component({
  selector: 'app-journal-information-tabs',
  imports: [NgbNavModule, TranslocoPipe, PersonData, InstitutionData],
  templateUrl: './journal-information-tabs.html',
  styleUrl: '../../../shared/keyboard-focus.css',
  styles: `
    :host .nav-pills .nav-link:not(.active) {
      color: var(--vz-body-color);
    }

    .journal-description {
      white-space: pre-line;
      text-align: justify;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalInformationTabs {
  readonly journal = input.required<JournalInformation>();
  readonly publishers = input<InstitutionView[] | null | undefined>();
  readonly activeTab = signal(1);
  protected readonly journalNameService = inject(JournalNameService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  private previousId?: number | null;
  readonly description = computed(() => {
    this.language();
    return this.journalNameService.getJournalDescription({
      descriptions: this.journal().descriptions ?? undefined,
    });
  });
  readonly scientificCategories = computed(() => this.journal().scientificCategories ?? []);
  readonly trCategories = computed(() => this.journal().trCategories ?? []);
  readonly ajscCategories = computed(() => this.journal().ajscCategories ?? []);

  constructor() {
    effect(() => {
      const id = this.journal().id;
      if (id !== this.previousId) {
        this.activeTab.set(1);
        this.previousId = id;
      }
    });
  }

  readonly formatText = (value: string | number | undefined | null) =>
    this.journalNameService.formatText(value);
  readonly formatBoolean = (value: boolean | undefined | null) =>
    value == null ? '-' : this.journalNameService.formatBoolean(value);
  readonly scientificCategoriesNames = () => this.categoryNames(this.scientificCategories());
  readonly trCategoriesNames = () => this.categoryNames(this.trCategories());
  readonly ajscCategoriesNames = () => this.categoryNames(this.ajscCategories());

  private categoryNames(categories: CategoryView[]): string {
    return categories
      .map((category) => this.journalNameService.getCategoryName(category) || '-')
      .sort()
      .join(', ');
  }
}
