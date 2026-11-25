import { Component, inject, Input } from '@angular/core';
import { PersonView } from '../../../core/backend';
import { JournalNameService } from '../../../core/services/journal-name-service';

@Component({
  selector: 'app-person-data',
  imports: [],
  templateUrl: './person-data.html',
  styleUrl: './person-data.css',
})
export class PersonData {
  @Input() person: PersonView | undefined;
  protected readonly journalNameService = inject(JournalNameService);
}
