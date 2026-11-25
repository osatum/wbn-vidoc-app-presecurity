import { Component, inject, Input } from '@angular/core';
import { CooperationDto } from '../../../../../core/backend';
import { JournalNameService } from '../../../../../core/services/journal-name-service';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-cooperation',
  imports: [TranslocoPipe],
  templateUrl: './cooperation.html',
  styleUrl: './cooperation.css',
})
export class Cooperation {
  @Input() cooperation?: CooperationDto;
  @Input() year?: number;
  protected readonly journalNameService = inject(JournalNameService);
}
