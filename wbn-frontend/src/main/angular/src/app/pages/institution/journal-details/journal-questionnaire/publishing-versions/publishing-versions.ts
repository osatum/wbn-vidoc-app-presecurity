import { Component, inject, Input } from '@angular/core';
import { PublishingVersionsDto } from '../../../../../core/backend';
import { JournalNameService } from '../../../../../core/services/journal-name-service';
import { TranslocoPipe } from '@jsverse/transloco';
import { QuestionnaireRowComponent } from '../questionnaire-row/questionnaire-row.component';
import { hasQuestionnaireOption } from '../questionnaire-presentation';

type ArticleMetadata = PublishingVersionsDto.ElectronicArticleMetadataEnum;

const ARTICLE_METADATA_ORDER: readonly ArticleMetadata[] = [
  'NATIONAL_KEYWORDS',
  'ENGLISH_KEYWORDS',
  'AUTHOR_AFFILIATION',
  'SEND_DATE',
  'ACCEPTED_DATE',
  'PUBLICATION_DATE',
  'AUTHOR_STATEMENT',
  'CONFLICT_OF_INTEREST',
  'SHARING_POLICY',
  'DOI',
  'ORCID',
];

const ARTICLE_METADATA_2025: readonly ArticleMetadata[] = [
  'PUBLICATION_DATE',
  'CONFLICT_OF_INTEREST',
  'SHARING_POLICY',
];

@Component({
  selector: 'app-publishing-versions',
  imports: [TranslocoPipe, QuestionnaireRowComponent],
  templateUrl: './publishing-versions.html',
  styleUrl: './publishing-versions.css',
})
export class PublishingVersions {
  @Input() publishingVersions?: PublishingVersionsDto;
  @Input() year?: number;
  protected readonly journalNameService = inject(JournalNameService);

  protected articleMetadata(options: unknown): readonly ArticleMetadata[] {
    return ARTICLE_METADATA_ORDER.filter(
      (option) =>
        (this.year === 2025 || !ARTICLE_METADATA_2025.includes(option)) &&
        hasQuestionnaireOption(options, option),
    );
  }

  protected get aopArticlesCount(): number | undefined {
    return this.year === 2025 && this.publishingVersions?.aop === true
      ? this.publishingVersions.aopArticlesCount ?? undefined
      : undefined;
  }
}
