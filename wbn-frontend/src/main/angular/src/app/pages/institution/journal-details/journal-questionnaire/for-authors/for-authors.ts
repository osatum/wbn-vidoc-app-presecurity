import { Component, inject, Input } from '@angular/core';
import { AuthorsFee, DigitalizationDto, StandardsDto } from '../../../../../core/backend';
import { JournalNameService } from '../../../../../core/services/journal-name-service';
import { QuestionnaireDescriptionItemComponent } from '../questionnaire-description-item/questionnaire-description-item.component';
import { QuestionnaireTableRowComponent } from '../questionnaire-table-row/questionnaire-table-row.component';
import { TranslocoPipe } from '@jsverse/transloco';
import AuthorInstructionsEnum = StandardsDto.AuthorInstructionsEnum;
import StatementsEnum = StandardsDto.StatementsEnum;
import { hasQuestionnaireOption } from '../questionnaire-presentation';


type AuthorsFeeGroup = {
  titleKey: string;
  fee?: AuthorsFee;
};

@Component({
  selector: 'app-for-authors',
  imports: [QuestionnaireDescriptionItemComponent, QuestionnaireTableRowComponent, TranslocoPipe],
  templateUrl: './for-authors.html',
  styleUrl: './for-authors.css',
})
export class ForAuthors {
  @Input() standards?: StandardsDto;
  @Input() digitalization?: DigitalizationDto;
  @Input() year?: number;
  protected readonly journalNameService = inject(JournalNameService);
  readonly isArticleStructure = () => this.articleStructure();
  readonly isLiteratureInstruction = () => this.literatureInstruction();

  readonly isOriginalKind = () => this.originalKind();
  readonly isAuthorContribution = () => this.authorContribution();
  readonly isFinanceSource = () => this.financeSource();

  protected isAiUsageScope(): boolean | undefined {
    return this.year === 2025 ? this.hasStatements(StatementsEnum.AiUsageScope) : undefined;
  }

  protected isAiUsageInstructions(): boolean | undefined {
    return this.year === 2025
      ? this.hasAuthorInstructions(AuthorInstructionsEnum.AiUsageInstructions)
      : undefined;
  }

  protected get authorFeeGroups(): AuthorsFeeGroup[] {
    return [
      {
        titleKey: 'page.institution.questionnaire.authors.feeGroups.submission',
        fee: this.standards?.submissionFee,
      },
      {
        titleKey: 'page.institution.questionnaire.authors.feeGroups.publication',
        fee: this.standards?.publicationFee,
      },
      {
        titleKey: 'page.institution.questionnaire.authors.feeGroups.openAccess',
        fee: this.standards?.openAccessFee,
      },
      {
        titleKey: 'page.institution.questionnaire.authors.feeGroups.apc',
        fee: this.standards?.apc,
      },
      {
        titleKey: 'page.institution.questionnaire.authors.feeGroups.rapidReview',
        fee: this.standards?.rapidReview,
      },
    ];
  }

  private articleStructure() {
    return this.hasAuthorInstructions(AuthorInstructionsEnum.ArticleStructure);
  }

  private literatureInstruction() {
    return this.hasAuthorInstructions(AuthorInstructionsEnum.LiteratureInstruction);
  }

  private originalKind() {
    return this.hasStatements(StatementsEnum.OriginalKind);
  }

  private authorContribution() {
    return this.hasStatements(StatementsEnum.AuthorContribution);
  }

  private financeSource() {
    return this.hasStatements(StatementsEnum.FinanceSource);
  }

  private hasAuthorInstructions(rule: StandardsDto.AuthorInstructionsEnum): boolean {
    return hasQuestionnaireOption(this.standards?.authorInstructions, rule);
  }

  private hasStatements(rule: StandardsDto.StatementsEnum): boolean {
    return hasQuestionnaireOption(this.standards?.statements, rule);
  }

  protected isFeeExist(fee: AuthorsFee) {
    if (fee === undefined) {
      return false;
    }
    if (fee.feeType === undefined || fee.feeType === null) {
      return false;
    }
    return true;
  }

  protected isNoFee(fee: AuthorsFee) {
    return fee.feeType === AuthorsFee.FeeTypeEnum.NoFee;
  }

  protected feeSelectionStatusKey(fee: AuthorsFee): string | undefined {
    if (fee.feeType !== AuthorsFee.FeeTypeEnum.CountrySpecificFee) {
      return undefined;
    }
    const status =
      fee.feeSelectionStatus ?? (fee.countrySpecificFeeSelection ? 'INCOMPLETE' : 'LEGACY');
    return `page.institution.questionnaire.authors.feeSelection.${status}`;
  }
}
