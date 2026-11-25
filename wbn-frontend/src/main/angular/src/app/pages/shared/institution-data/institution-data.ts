import { Component, inject, Input } from '@angular/core';
import { JournalNameService } from '../../../core/services/journal-name-service';
import { InstitutionView } from '../../../core/backend';

@Component({
  selector: 'app-institution-data',
  imports: [],
  templateUrl: './institution-data.html',
  styleUrl: './institution-data.css',
})
export class InstitutionData {
  @Input() institution: InstitutionView | undefined;
  protected readonly journalNameService = inject(JournalNameService);
}
