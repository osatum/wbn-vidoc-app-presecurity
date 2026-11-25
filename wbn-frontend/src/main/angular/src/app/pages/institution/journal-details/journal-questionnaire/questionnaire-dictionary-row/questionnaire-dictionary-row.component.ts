import { Component, computed, inject, input } from '@angular/core';
import { JournalNameService } from '../../../../../core/services/journal-name-service';

type DictionaryCollection = readonly unknown[] | ReadonlySet<unknown> | null | undefined;

@Component({
  selector: 'app-questionnaire-dictionary-row',
  host: {
    class: 'd-block',
  },
  template: `
    <div class="row g-0" [class.bg-light-subtle]="highlight()">
      <div class="col-4 col-md-5 p-2 header-custom" [class.border-bottom]="borderBottom()">
        {{ description() }}
      </div>
      <div class="col-8 col-md-7 p-2" [class.border-bottom]="borderBottom()">
        @if (items().length) {
          @for (item of items(); track item) {
            <div>{{ journalNameService.getJournalDictionaryItem(item) }}</div>
          }
        } @else {
          -
        }
      </div>
    </div>
  `,
})
export class QuestionnaireDictionaryRowComponent {
  readonly description = input.required<string>();
  readonly values = input<DictionaryCollection>();
  readonly highlight = input(false);
  readonly borderBottom = input(true);

  protected readonly journalNameService = inject(JournalNameService);

  protected readonly items = computed((): readonly unknown[] => {
    const values = this.values();

    if (values === null || values === undefined) {
      return [];
    }

    if (Array.isArray(values)) {
      return values;
    }

    return Array.from(values);
  });
}
