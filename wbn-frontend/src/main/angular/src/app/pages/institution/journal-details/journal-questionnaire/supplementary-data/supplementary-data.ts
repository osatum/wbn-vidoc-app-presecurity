import { Component, inject, Input } from '@angular/core';
import {
  DigitalizationDto,
  JournalDto,
  StabilityDto,
  StandardsDto,
} from '../../../../../core/backend';
import { JournalNameService } from '../../../../../core/services/journal-name-service';
import {
  QuestionnaireDescriptionItemComponent
} from '../questionnaire-description-item/questionnaire-description-item.component';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-supplementary-data',
  imports: [QuestionnaireDescriptionItemComponent, TranslocoPipe],
  templateUrl: './supplementary-data.html',
  styleUrl: './supplementary-data.css',
})
export class SupplementaryData {
  @Input() journal?: JournalDto;
  @Input() stability?: StabilityDto;
  @Input() year?: number;
  protected readonly journalNameService = inject(JournalNameService);
}
