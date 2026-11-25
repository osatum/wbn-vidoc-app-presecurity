import {
  ArticlePublished,
  AuthorsFee,
  DigitalizationDto,
  Issn,
  JournalTitle,
  JournalView,
  PaidArticleView,
  PublishingVersionsDto,
  RatingIcvView,
  SocialMedias,
  StabilityDto,
  StandardsDto,
  SupplementaryDataDto,
} from '../../core/backend';

export const JOURNAL_KIND = {
  SCIENTIFIC: 'journal.dictionary.kind.scientific',
  OTHER: 'journal.dictionary.kind.other',
  POPULAR_SCIENCE: 'journal.dictionary.kind.popular-science',
} satisfies Record<JournalView.KindEnum, string>;

export const JOURNAL_CURRENT_REASON_NOT_INDEXING = {
  dec01: 'journal.dictionary.current-reason-not-indexing.dec01',
  dec02: 'journal.dictionary.current-reason-not-indexing.dec02',
  dec03: 'journal.dictionary.current-reason-not-indexing.dec03',
  dec04: 'journal.dictionary.current-reason-not-indexing.dec04',
  dec05: 'journal.dictionary.current-reason-not-indexing.dec05',
  dec06: 'journal.dictionary.current-reason-not-indexing.dec06',
  dec07: 'journal.dictionary.current-reason-not-indexing.dec07',
  dec08: 'journal.dictionary.current-reason-not-indexing.dec08',
  dec09: 'journal.dictionary.current-reason-not-indexing.dec09',
  dec10: 'journal.dictionary.current-reason-not-indexing.dec10',
  dec11: 'journal.dictionary.current-reason-not-indexing.dec11',
  dec12: 'journal.dictionary.current-reason-not-indexing.dec12',
} satisfies Record<JournalView.CurrentReasonNotIndexingEnum, string>;

export const JOURNAL_REASON_NOT_INDEXING = JOURNAL_CURRENT_REASON_NOT_INDEXING satisfies Record<
  RatingIcvView.ReasonNotIndexingEnum,
  string
>;

export const JOURNAL_TITLE = {
  CITATION: 'journal.dictionary.title.citation',
  ORIGINAL: 'journal.dictionary.title.original',
  ENGLISH: 'journal.dictionary.title.english',
  SHORT: 'journal.dictionary.title.short',
} satisfies Record<JournalTitle.TitleTypeEnum, string>;

export const JOURNAL_PAID_TYPE = {
  FREE_ARTICLES: 'journal.dictionary.paid-type.free-articles',
  ALL_ARTICLES_PAID: 'journal.dictionary.paid-type.all-articles-paid',
  PAID_ARTICLES: 'journal.dictionary.paid-type.paid-articles',
} satisfies Record<PaidArticleView.PaidTypeEnum, string>;

export const ISSN_TYPE = {
  P: 'journal.dictionary.issn-type.print',
  E: 'journal.dictionary.issn-type.electronic',
} satisfies Record<Issn.TypeEnum, string>;

export const PUBLISHING_ARTICLES_FORMATS = {
  XML: 'XML',
  PDF: 'PDF',
  SCAN: 'journal.dictionary.common.scan',
  OTHER: 'journal.dictionary.common.other',
  HTML: 'HTML',
} satisfies Record<PublishingVersionsDto.ArticlesFormatsEnum, string>;

export const PUBLISHING_COVER_INFORMATION = {
  ISSN: 'ISSN',
  YEAR: 'journal.dictionary.common.year',
  TOME: 'journal.dictionary.common.volume',
  NUMBER: 'journal.dictionary.common.number',
  MONTH: 'journal.dictionary.common.month',
  NONE: 'journal.dictionary.common.none',
} satisfies Record<PublishingVersionsDto.CoverInformationEnum, string>;

export const PUBLISHING_VERSION_INFORMATION = {
  EISSN: 'eISSN',
  YEAR: 'journal.dictionary.common.year',
  VOLUME_NUMBER_ISSUE_MONTH: 'journal.dictionary.publishing.version-information.volume-number-issue-month',
  NONE: 'journal.dictionary.common.none',
} satisfies Record<PublishingVersionsDto.ElectronicVersionInformationEnum, string>;

export const PUBLISHING_ELECTRONIC_ARTICLE_METADATA = {
  NATIONAL_KEYWORDS: 'journal.dictionary.article-metadata.national-keywords',
  ENGLISH_KEYWORDS: 'journal.dictionary.article-metadata.english-keywords',
  AUTHOR_AFFILIATION: 'journal.dictionary.article-metadata.author-affiliation',
  SEND_DATE: 'journal.dictionary.article-metadata.send-date',
  ACCEPTED_DATE: 'journal.dictionary.article-metadata.accepted-date',
  AUTHOR_STATEMENT: 'journal.dictionary.article-metadata.author-statement',
  DOI: 'DOI',
  GICID: 'GICID',
  ORCID: 'ORCID',
  PUBLICATION_DATE: 'journal.dictionary.article-metadata.publication-date',
  CONFLICT_OF_INTEREST: 'journal.dictionary.article-metadata.conflict-of-interest',
  SHARING_POLICY: 'journal.dictionary.article-metadata.sharing-policy',
  NONE: 'journal.dictionary.common.none',
} satisfies Record<PublishingVersionsDto.ElectronicArticleMetadataEnum, string>;

export const PUBLISHING_PRINTED_ARTICLE_METADATA = {
  NATIONAL_KEYWORDS: 'journal.dictionary.article-metadata.national-keywords',
  ENGLISH_KEYWORDS: 'journal.dictionary.article-metadata.english-keywords',
  AUTHOR_AFFILIATION: 'journal.dictionary.article-metadata.author-affiliation',
  SEND_DATE: 'journal.dictionary.article-metadata.send-date-short',
  ACCEPTED_DATE: 'journal.dictionary.article-metadata.accepted-date-short',
  AUTHOR_STATEMENT: 'journal.dictionary.article-metadata.author-statement-short',
  DOI: 'DOI',
  GICID: 'GICID',
  ORCID: 'ORCID',
  PUBLICATION_DATE: 'journal.dictionary.article-metadata.publication-date',
  CONFLICT_OF_INTEREST: 'journal.dictionary.article-metadata.conflict-of-interest',
  SHARING_POLICY: 'journal.dictionary.article-metadata.sharing-policy',
  NONE: 'journal.dictionary.common.none',
} satisfies Record<PublishingVersionsDto.PrintedArticleMetadataEnum, string>;

export const PUBLISHING_POLICY = {
  EXTENDED_ELECTRONIC_VERSIONS: 'journal.dictionary.publishing.policy.extended-electronic-versions',
  SAME_ARTICLES: 'journal.dictionary.publishing.policy.same-articles',
  SAME_ARTICLES_AND_OTHERS: 'journal.dictionary.publishing.policy.same-articles-and-others',
  PRINTED_MORE_THAN_ELECTRONIC: 'journal.dictionary.publishing.policy.printed-more-than-electronic',
  ELECTRONIC_MORE_THAN_PRINTED: 'journal.dictionary.publishing.policy.electronic-more-than-printed',
} satisfies Record<PublishingVersionsDto.PublishingPolicyEnum, string>;

export const STABILITY_FIRST_NUMBER_MONTH = {
  DEC_YEAR_BEFORE: 'journal.dictionary.month.dec-year-before',
  JANUARY: 'journal.dictionary.month.january',
  FEBRUARY: 'journal.dictionary.month.february',
  MARCH: 'journal.dictionary.month.march',
  APRIL: 'journal.dictionary.month.april',
  MAY: 'journal.dictionary.month.may',
  JUNE: 'journal.dictionary.month.june',
  JULY: 'journal.dictionary.month.july',
  AUGUST: 'journal.dictionary.month.august',
  SEPTEMBER: 'journal.dictionary.month.september',
  OCTOBER: 'journal.dictionary.month.october',
  NOVEMBER: 'journal.dictionary.month.november',
  DECEMBER: 'journal.dictionary.month.december',
  JAN_NEXT_YEAR: 'journal.dictionary.month.jan-next-year',
  OTHER: 'journal.dictionary.common.other',
} satisfies Record<StabilityDto.FirstNumberMonthEnum, string>;

export const ARTICLE_PUBLISHED_FREQUENCY = {
  c: 'journal.dictionary.frequency.biweekly',
  b: 'journal.dictionary.frequency.bimonthly',
  f: 'journal.dictionary.frequency.semiannual',
  g: 'journal.dictionary.frequency.irregular',
  e: 'journal.dictionary.frequency.weekly',
  q: 'journal.dictionary.frequency.quarterly',
  m: 'journal.dictionary.frequency.monthly',
  a: 'journal.dictionary.frequency.annual',
  k: 'journal.dictionary.frequency.continuous',
  t: 'journal.dictionary.frequency.three-times-year',
} satisfies Record<ArticlePublished.FrequencyCodeEnum, string>;

export const ARTICLE_PUBLISHED_FREQUENCY_VALUE = {
  c: 24,
  b: 6,
  f: 2,
  g: 100,
  e: 48,
  q: 4,
  m: 12,
  a: 1,
  k: 101,
  t: 3,
} satisfies Record<ArticlePublished.FrequencyCodeEnum, number>;

export const STANDARDS_AUTHOR_INSTRUCTIONS = {
  ARTICLE_STRUCTURE: 'journal.dictionary.standards.author-instructions.article-structure',
  LITERATURE_INSTRUCTION: 'journal.dictionary.standards.author-instructions.literature-instruction',
  NO_INSTRUCTION: 'journal.dictionary.standards.author-instructions.no-instruction',
  AI_USAGE_INSTRUCTIONS: 'journal.dictionary.standards.author-instructions.ai-usage-instructions',
  NONE: 'journal.dictionary.common.none',
} satisfies Record<StandardsDto.AuthorInstructionsEnum, string>;

export const STANDARDS_STATEMENTS = {
  CONFLICT: 'journal.dictionary.standards.statements.conflict',
  FINANCE_SOURCE: 'journal.dictionary.standards.statements.finance-source',
  AUTHOR_CONTRIBUTION: 'journal.dictionary.standards.statements.author-contribution',
  ORIGINAL_KIND: 'journal.dictionary.standards.statements.original-kind',
  AI_USAGE_SCOPE: 'journal.dictionary.standards.statements.ai-usage-scope',
  NONE: 'journal.dictionary.common.none',
} satisfies Record<StandardsDto.StatementsEnum, string>;

export const STANDARDS_REVIEW_RULES = {
  PUBLISHED_REVIEWERS_LIST: 'journal.dictionary.standards.review-rules.published-reviewers-list',
  MIN_2_REVIEWERS: 'journal.dictionary.standards.review-rules.min-2-reviewers',
  REVIEWER_STATEMENT: 'journal.dictionary.standards.review-rules.reviewer-statement',
  PREPARE_REVIEW: 'journal.dictionary.standards.review-rules.prepare-review',
  PUBLIC_FORM: 'journal.dictionary.standards.review-rules.public-form',
  PUBLIC_CRITERIA: 'journal.dictionary.standards.review-rules.public-criteria',
  NONE: 'journal.dictionary.common.none',
} satisfies Record<StandardsDto.ReviewRulesEnum, string>;

export const AUTHORS_FEE_TYPE = {
  NO_FEE: 'journal.dictionary.authors-fee.no-fee',
  ONE_FEE: 'journal.dictionary.authors-fee.one-fee',
  COUNTRY_SPECIFIC_FEE: 'journal.dictionary.authors-fee.country-specific-fee',
  VARIOUS_FEE: 'journal.dictionary.authors-fee.various-fee',
} satisfies Record<AuthorsFee.FeeTypeEnum, string>;

export type AuthorsFeeDetailDescription = 'FOR_ALL' | 'INTERNAL_AUTHORS' | 'EXTERNAL_AUTHORS';

export const AUTHORS_FEE_DETAIL_DESCRIPTION_VALUES = {
  ForAll: 'FOR_ALL',
  InternalAuthors: 'INTERNAL_AUTHORS',
  ExternalAuthors: 'EXTERNAL_AUTHORS',
} satisfies Record<string, AuthorsFeeDetailDescription>;

export const AUTHORS_FEE_DETAIL_DESCRIPTION = {
  FOR_ALL: 'journal.dictionary.authors-fee.detail-description.for-all',
  INTERNAL_AUTHORS: 'journal.dictionary.authors-fee.detail-description.internal-authors',
  EXTERNAL_AUTHORS: 'journal.dictionary.authors-fee.detail-description.external-authors',
} satisfies Record<AuthorsFeeDetailDescription, string>;

export const SUPPLEMENTARY_DATA_KIND = {
  SCIENTIFIC: 'journal.dictionary.kind.scientific',
  OTHER: 'journal.dictionary.kind.other',
  POPULAR_SCIENCE: 'journal.dictionary.kind.popular-science',
} satisfies Record<SupplementaryDataDto.KindEnum, string>;

export const DIGITALIZATION_EDITORIAL_TOOLS = {
  ANTI_PLAGIARISM_SYSTEM: 'journal.dictionary.digitalization.editorial-tools.anti-plagiarism-system',
  EDITORIAL_SYSTEM: 'journal.dictionary.digitalization.editorial-tools.editorial-system',
  MANUSCRIPT_SYSTEM: 'journal.dictionary.digitalization.editorial-tools.manuscript-system',
  EXPORT_TOOLS: 'journal.dictionary.digitalization.editorial-tools.export-tools',
  STATISTICS: 'journal.dictionary.digitalization.editorial-tools.statistics',
  NEWSLETTERS: 'journal.dictionary.digitalization.editorial-tools.newsletters',
  NONE: 'journal.dictionary.common.none',
} satisfies Record<DigitalizationDto.EditorialToolsEnum, string>;

export const DIGITALIZATION_CITATIONS_STYLE = {
  Cell: 'Cell',
  Science: 'Science',
  Nature: 'Nature',
  MLA: 'MLA',
  Bibtex: 'BibTeX',
  Harvard: 'Harvard',
  APA: 'APA',
  Vancouver: 'Vancouver',
  ISO690: 'ISO 690',
  Chicago: 'Chicago',
  IEEE: 'IEEE',
  Other: 'journal.dictionary.common.other',
  Oxford: 'Oxford',
  ACM: 'ACM',
  AMA: 'AMA',
} satisfies Record<DigitalizationDto.CitationsStyleEnum, string>;

export const DIGITALIZATION_CITATIONS_TOOLS = {
  BIBTEX: 'BibTeX',
  ENDNOTE: 'EndNote',
  REFMAN: 'RefMan',
  REFW: 'RefWorks',
  NONE: 'journal.dictionary.common.none',
  OTH: 'journal.dictionary.common.other-plural',
  PP: 'ICI Publishers Panel',
} satisfies Record<DigitalizationDto.CitationsToolsEnum, string>;

export const DIGITALIZATION_ARTICLE_PUBLISHED_LICENSE = {
  NONE: 'journal.dictionary.common.none',
  CC_BY: 'CC BY',
  CC_BY_SA: 'CC BY-SA',
  CC_BY_ND: 'CC BY-ND',
  CC_BY_NC: 'CC BY-NC',
  CC_BY_NC_SA: 'CC BY-NC-SA',
  CC_BY_NC_ND: 'CC BY-NC-ND',
  AUTHOR_PREFERENCE: 'journal.dictionary.digitalization.article-license.author-preference',
} satisfies Record<DigitalizationDto.ArticlePublishedLicenseEnum, string>;

export const DIGITALIZATION_DOI_TYPE = {
  ALL: 'journal.dictionary.digitalization.doi-type.all',
  PART: 'journal.dictionary.digitalization.doi-type.part',
  NONE: 'journal.dictionary.common.none',
} satisfies Record<DigitalizationDto.DoiTypeEnum, string>;

export type DigitalizationPublishingToolId =
  | 'AMSYS'
  | 'DRU'
  | 'JMSYS'
  | 'EMANA'
  | 'EDSYS'
  | 'EJOUR'
  | 'ELSEV'
  | 'ICI'
  | 'JOOML'
  | 'JOUSYS'
  | 'LODEL'
  | 'OJS'
  | 'WORDPRESS';

export const DIGITALIZATION_PUBLISHING_TOOLS_IDS = {
  AMSYS: 'journal.dictionary.digitalization.publishing-tools.amsys',
  DRU: 'journal.dictionary.digitalization.publishing-tools.dru',
  JMSYS: 'journal.dictionary.digitalization.publishing-tools.jmsys',
  EMANA: 'journal.dictionary.digitalization.publishing-tools.emana',
  EDSYS: 'journal.dictionary.digitalization.publishing-tools.edsys',
  EJOUR: 'journal.dictionary.digitalization.publishing-tools.ejour',
  ELSEV: 'journal.dictionary.digitalization.publishing-tools.elsev',
  ICI: 'journal.dictionary.digitalization.publishing-tools.ici',
  JOOML: 'journal.dictionary.digitalization.publishing-tools.jooml',
  JOUSYS: 'journal.dictionary.digitalization.publishing-tools.jousys',
  LODEL: 'journal.dictionary.digitalization.publishing-tools.lodel',
  OJS: 'journal.dictionary.digitalization.publishing-tools.ojs',
  WORDPRESS: 'journal.dictionary.digitalization.publishing-tools.wordpress',
} satisfies Record<DigitalizationPublishingToolId, string>;

export const SOCIAL_MEDIAS_IDS = {
  FACEBOOK: 'Facebook',
  INSTAGRAM: 'Instagram',
  LINKEDIN: 'LinkedIn',
  X: 'X',
  YOUTUBE: 'YouTube',
} satisfies Record<SocialMedias.IdsEnum, string>;
