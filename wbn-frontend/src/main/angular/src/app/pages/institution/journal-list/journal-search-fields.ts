import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import type { JournalList } from './journal-list';
import {
  MultiValueSelect,
  MultiValueSelectOption,
} from '../../../shared/multi-value-select/multi-value-select';

type DictionaryField = 'countryCodes' | 'languageCodes';

@Component({
  selector: '[app-journal-search-fields]',
  imports: [ReactiveFormsModule, TranslocoPipe, MultiValueSelect],
  templateUrl: './journal-search-fields.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalSearchFields {
  readonly form = input.required<JournalList['filterForm']>();
  readonly countries = input<readonly MultiValueSelectOption[]>([]);
  readonly languages = input<readonly MultiValueSelectOption[]>([]);
  readonly countryLoading = input(false);
  readonly languageLoading = input(false);
  readonly countryError = input(false);
  readonly languageError = input(false);
  readonly codesChange = output<{ field: DictionaryField; codes: string[] }>();
  readonly textFields = ['journalTitle', 'issn', 'publisher'] as const;
  readonly dictionaryFields = ['countryCodes', 'languageCodes'] as const;
}
