import { Component, inject, Input } from '@angular/core';
import { DigitalizationDto } from '../../../../../core/backend';
import { JournalNameService } from '../../../../../core/services/journal-name-service';
import {
  QuestionnaireDescriptionItemComponent
} from '../questionnaire-description-item/questionnaire-description-item.component';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-scientific-databases',
  imports: [QuestionnaireDescriptionItemComponent, TranslocoPipe],
  templateUrl: './scientific-databases.html',
  styleUrl: './scientific-databases.css',
})
export class ScientificDatabases {
  @Input() digitalization?: DigitalizationDto;
  @Input() year?: number;
  protected readonly journalNameService = inject(JournalNameService);

  getYearM2() {
    return this.year ? this.year - 2 : undefined;
  }
  getYearM1() {
    return this.year ? this.year - 1 : undefined;
  }
}
