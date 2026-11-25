import { Component, inject, Input } from '@angular/core';
import { DigitalizationDto } from '../../../../../core/backend';
import { JournalNameService } from '../../../../../core/services/journal-name-service';
import { QuestionnaireTableRowComponent } from '../questionnaire-table-row/questionnaire-table-row.component';
import { TranslocoPipe } from '@jsverse/transloco';
import { questionnaire2025Link } from '../questionnaire-presentation';


@Component({
  selector: 'app-digitalization',
  imports: [QuestionnaireTableRowComponent, TranslocoPipe],
  templateUrl: './digitalization.html',
  styleUrl: './digitalization.css',
})
export class Digitalization {
  @Input() digitalization?: DigitalizationDto;
  @Input() year?: number;
  protected readonly journalNameService = inject(JournalNameService);
  protected readonly linkFor2025 = questionnaire2025Link;

  readonly isAntiPlagiarismSystem = () => this.antiPlagiarismSystem();
  readonly isStatistics = () => this.statistics();
  readonly isNewsletters = () => this.newsletters();

  private hasEditorialToolsEnum(rule: DigitalizationDto.EditorialToolsEnum): boolean {
    const tools: unknown = this.digitalization?.editorialTools;

    if (tools === undefined) {
      return false;
    }

    if (tools instanceof Set) {
      return tools.has(rule);
    }

    return Array.isArray(tools) && tools.includes(rule);
  }

  private antiPlagiarismSystem() {
    return this.hasEditorialToolsEnum(DigitalizationDto.EditorialToolsEnum.AntiPlagiarismSystem);
  }

  private statistics() {
    return this.hasEditorialToolsEnum(DigitalizationDto.EditorialToolsEnum.Statistics);
  }

  private newsletters() {
    return this.hasEditorialToolsEnum(DigitalizationDto.EditorialToolsEnum.Newsletters);
  }

  websiteLanguagesNames(): string {
    if (this.digitalization === undefined || this.digitalization.websiteLanguages === undefined) {
      return '-';
    }
    return this.digitalization?.websiteLanguages
      .map((category) => this.journalNameService.getLanguageName(category) || '-')
      .sort()
      .join(', ');
  }
}
