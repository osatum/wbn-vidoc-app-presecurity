import { Component, inject, Input } from '@angular/core';
import { StandardsDto } from '../../../../../core/backend';
import { JournalNameService } from '../../../../../core/services/journal-name-service';
import { QuestionnaireTableRowComponent } from '../questionnaire-table-row/questionnaire-table-row.component';
import { TranslocoPipe } from '@jsverse/transloco';
import { hasQuestionnaireOption, questionnaire2025Link } from '../questionnaire-presentation';

@Component({
  selector: 'app-standards',
  imports: [QuestionnaireTableRowComponent, TranslocoPipe],
  templateUrl: './standards.html',
  styleUrl: './standards.css',
})
export class Standards {
  @Input() standards?: StandardsDto;
  @Input() year?: number;
  protected readonly journalNameService = inject(JournalNameService);

  protected readonly linkFor2025 = questionnaire2025Link;
  readonly isPublishedReviewersList = () => this.publishedReviewersList();
  readonly isMin2Reviewers = () => this.min2Reviewers();
  readonly isPublicForm = () => this.publicForm();
  readonly isPublicCriteria = () => this.publicCriteria();
  readonly isReviewerStatement = () => this.reviewerStatement();
  readonly isPrepareReview = () => this.prepareReview();

  private publishedReviewersList(): boolean {
    return this.hasReviewRule(StandardsDto.ReviewRulesEnum.PublishedReviewersList);
  }

  private min2Reviewers(): boolean {
    return this.hasReviewRule(StandardsDto.ReviewRulesEnum.Min2Reviewers);
  }

  private publicForm(): boolean {
    return this.hasReviewRule(StandardsDto.ReviewRulesEnum.PublicForm);
  }

  private publicCriteria(): boolean {
    return this.hasReviewRule(StandardsDto.ReviewRulesEnum.PublicCriteria);
  }

  private hasReviewRule(rule: StandardsDto.ReviewRulesEnum): boolean {
    return hasQuestionnaireOption(this.standards?.reviewRules, rule);
  }

  private reviewerStatement() {
    return this.hasReviewRule(StandardsDto.ReviewRulesEnum.ReviewerStatement);
  }

  private prepareReview() {
    return this.hasReviewRule(StandardsDto.ReviewRulesEnum.PrepareReview);
  }
}
