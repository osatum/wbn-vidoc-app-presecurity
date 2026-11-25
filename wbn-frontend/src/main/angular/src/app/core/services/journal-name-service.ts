import { inject, Injectable } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
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
} from '../backend';
import {
  ARTICLE_PUBLISHED_FREQUENCY,
  ARTICLE_PUBLISHED_FREQUENCY_VALUE,
  AUTHORS_FEE_DETAIL_DESCRIPTION,
  AUTHORS_FEE_DETAIL_DESCRIPTION_VALUES,
  AUTHORS_FEE_TYPE,
  DIGITALIZATION_ARTICLE_PUBLISHED_LICENSE,
  DIGITALIZATION_CITATIONS_STYLE,
  DIGITALIZATION_CITATIONS_TOOLS,
  DIGITALIZATION_DOI_TYPE,
  DIGITALIZATION_EDITORIAL_TOOLS,
  DIGITALIZATION_PUBLISHING_TOOLS_IDS,
  ISSN_TYPE,
  JOURNAL_CURRENT_REASON_NOT_INDEXING,
  JOURNAL_KIND,
  JOURNAL_PAID_TYPE,
  JOURNAL_TITLE,
  JOURNAL_REASON_NOT_INDEXING,
  PUBLISHING_ARTICLES_FORMATS,
  PUBLISHING_COVER_INFORMATION,
  PUBLISHING_ELECTRONIC_ARTICLE_METADATA,
  PUBLISHING_POLICY,
  PUBLISHING_PRINTED_ARTICLE_METADATA,
  PUBLISHING_VERSION_INFORMATION,
  SOCIAL_MEDIAS_IDS,
  STABILITY_FIRST_NUMBER_MONTH,
  STANDARDS_AUTHOR_INSTRUCTIONS,
  STANDARDS_REVIEW_RULES,
  STANDARDS_STATEMENTS,
  SUPPLEMENTARY_DATA_KIND,
} from '../../pages/institution/journal-ui-definitions';

type LanguageNameCodeType = {
  namePl?: string;
  nameEn?: string;
  nameRu?: string;
};

@Injectable({
  providedIn: 'root',
})
export class JournalNameService {
  getJournalDictionaryItem(key: unknown): string {
    if (key === undefined || key === null) {
      return '-';
    }

    const dictionaryValue =
      this.getDictionaryValue(key, JournalView.KindEnum, JOURNAL_KIND) ??
      this.getDictionaryValue(
        key,
        JournalView.CurrentReasonNotIndexingEnum,
        JOURNAL_CURRENT_REASON_NOT_INDEXING,
      ) ??
      this.getDictionaryValue(
        key,
        RatingIcvView.ReasonNotIndexingEnum,
        JOURNAL_REASON_NOT_INDEXING,
      ) ??
      this.getDictionaryValue(key, PaidArticleView.PaidTypeEnum, JOURNAL_PAID_TYPE) ??
      this.getDictionaryValue(key, JournalTitle.TitleTypeEnum, JOURNAL_TITLE) ??
      this.getDictionaryValue(key, Issn.TypeEnum, ISSN_TYPE) ??
      this.getDictionaryValue(
        key,
        PublishingVersionsDto.ArticlesFormatsEnum,
        PUBLISHING_ARTICLES_FORMATS,
      ) ??
      this.getDictionaryValue(
        key,
        PublishingVersionsDto.CoverInformationEnum,
        PUBLISHING_COVER_INFORMATION,
      ) ??
      this.getDictionaryValue(
        key,
        PublishingVersionsDto.ElectronicVersionInformationEnum,
        PUBLISHING_VERSION_INFORMATION,
      ) ??
      this.getDictionaryValue(
        key,
        PublishingVersionsDto.ElectronicArticleMetadataEnum,
        PUBLISHING_ELECTRONIC_ARTICLE_METADATA,
      ) ??
      this.getDictionaryValue(
        key,
        PublishingVersionsDto.PrintedArticleMetadataEnum,
        PUBLISHING_PRINTED_ARTICLE_METADATA,
      ) ??
      this.getDictionaryValue(key, PublishingVersionsDto.PublishingPolicyEnum, PUBLISHING_POLICY) ??
      this.getDictionaryValue(
        key,
        StabilityDto.FirstNumberMonthEnum,
        STABILITY_FIRST_NUMBER_MONTH,
      ) ??
      this.getDictionaryValue(
        key,
        ArticlePublished.FrequencyCodeEnum,
        ARTICLE_PUBLISHED_FREQUENCY,
      ) ??
      this.getArticlePublishedFrequencyValue(key) ??
      this.getDictionaryValue(
        key,
        StandardsDto.AuthorInstructionsEnum,
        STANDARDS_AUTHOR_INSTRUCTIONS,
      ) ??
      this.getDictionaryValue(key, StandardsDto.StatementsEnum, STANDARDS_STATEMENTS) ??
      this.getDictionaryValue(key, StandardsDto.ReviewRulesEnum, STANDARDS_REVIEW_RULES) ??
      this.getDictionaryValue(key, AuthorsFee.FeeTypeEnum, AUTHORS_FEE_TYPE) ??
      this.getDictionaryValue(
        key,
        AUTHORS_FEE_DETAIL_DESCRIPTION_VALUES,
        AUTHORS_FEE_DETAIL_DESCRIPTION,
      ) ??
      this.getDictionaryValue(key, SupplementaryDataDto.KindEnum, SUPPLEMENTARY_DATA_KIND) ??
      this.getDictionaryValue(
        key,
        DigitalizationDto.EditorialToolsEnum,
        DIGITALIZATION_EDITORIAL_TOOLS,
      ) ??
      this.getDictionaryValue(
        key,
        DigitalizationDto.CitationsStyleEnum,
        DIGITALIZATION_CITATIONS_STYLE,
      ) ??
      this.getDictionaryValue(
        key,
        DigitalizationDto.CitationsToolsEnum,
        DIGITALIZATION_CITATIONS_TOOLS,
      ) ??
      this.getDictionaryValue(
        key,
        DigitalizationDto.ArticlePublishedLicenseEnum,
        DIGITALIZATION_ARTICLE_PUBLISHED_LICENSE,
      ) ??
      this.getDictionaryValue(key, DigitalizationDto.DoiTypeEnum, DIGITALIZATION_DOI_TYPE) ??
      this.getDictionaryValueFromRecord(key, DIGITALIZATION_PUBLISHING_TOOLS_IDS) ??
      this.getDictionaryValue(key, SocialMedias.IdsEnum, SOCIAL_MEDIAS_IDS);

    if (dictionaryValue !== undefined) {
      return this.translate.translate(dictionaryValue);
    }

    throw new Error('Method not implemented.');
  }

  getAuthorsFeeDetailDescription(description: string | null | undefined): string {
    if (!description?.trim()) {
      return '-';
    }

    const dictionaryValue = this.getDictionaryValue(
      description,
      AUTHORS_FEE_DETAIL_DESCRIPTION_VALUES,
      AUTHORS_FEE_DETAIL_DESCRIPTION,
    );

    return dictionaryValue === undefined ? description : this.translate.translate(dictionaryValue);
  }

  private getDictionaryValue<T extends string>(
    key: unknown,
    enumValues: Record<string, T>,
    dictionary: Record<T, string>,
  ): string | undefined {
    if (!Object.values(enumValues).includes(key as T)) {
      return undefined;
    }

    return dictionary[key as T] ?? '-';
  }

  private getDictionaryValueFromRecord(
    key: unknown,
    dictionary: Record<string, string>,
  ): string | undefined {
    if (typeof key !== 'string') {
      return undefined;
    }

    return dictionary[key];
  }

  private getArticlePublishedFrequencyValue(key: unknown): string | undefined {
    if (typeof key !== 'number') {
      return undefined;
    }

    const frequency = Object.entries(ARTICLE_PUBLISHED_FREQUENCY_VALUE).find(
      ([, value]) => value === key,
    )?.[0] as ArticlePublished.FrequencyCodeEnum | undefined;

    if (frequency === undefined) {
      return undefined;
    }

    const labelKey = ARTICLE_PUBLISHED_FREQUENCY[frequency];
    return labelKey === undefined ? '-' : this.translate.translate(labelKey);
  }

  protected readonly translate = inject(TranslocoService);

  public getJournalDescription(journal: JournalView | null | undefined): string | undefined {
    const descriptions = journal?.descriptions;

    if (!descriptions?.length) {
      return this.translate.translate('nd');
    }

    const activeLanguage = this.activeLanguage();
    const selectedDescription = descriptions.find(
      (description) => description.type?.toLowerCase() === activeLanguage,
    );
    const englishDescription = descriptions.find(
      (description) => description.type?.toLowerCase() === 'en',
    );

    return selectedDescription?.content || englishDescription?.content;
  }

  public getCountryName(country: LanguageNameCodeType | undefined): string | undefined {
    if (country == undefined) {
      return this.translate.translate('nd');
    }

    return this.getLocalizedName(country);
  }

  public getLanguageName(language: LanguageNameCodeType | undefined): string | undefined {
    if (language == undefined) {
      return this.translate.translate('nd');
    }

    return this.getLocalizedName(language);
  }

  private getLocalizedName(value: LanguageNameCodeType): string | undefined {
    const activeLanguage = this.activeLanguage();

    if (activeLanguage === 'ru') {
      return value.nameRu || value.nameEn;
    }

    if (activeLanguage === 'pl') {
      return value.namePl || value.nameEn;
    }

    return value.nameEn;
  }

  private activeLanguage(): string {
    return this.translate.getActiveLang().toLowerCase();
  }

  isEmptyValue(value: any): boolean {
    // null lub undefined → dla wszystkich typów traktujemy jako puste
    if (value === null || value === undefined) {
      return true;
    }

    // string → pusty string
    if (typeof value === 'string') {
      return value.trim().length === 0;
    }

    // number → tu tylko null/undefined, więc skoro przeszło dalej, to OK
    if (typeof value === 'number') {
      return false;
    }

    // boolean → tylko null/undefined, tu więc zawsze OK
    if (typeof value === 'boolean') {
      return false;
    }

    // array → pusta tablica
    if (Array.isArray(value)) {
      return value.length === 0;
    }
    console.log(value);
    if (value instanceof Set) {
      return new Set(value).size === 0;
    }

    // opcjonalnie inne typy
    return false;
  }

  public getCategoryName(category: LanguageNameCodeType | undefined): string | undefined {
    if (category == undefined) {
      return this.translate.translate('-');
    }

    return this.getLocalizedName(category);
  }

  getMessage(key: any, value: any) {
    if (this.isEmptyValue(value)) {
      return this.translate.translate('nd');
    }
    return this.translate.translate(key + value);
  }
  formatText(value: string | number | undefined | null): string {
    return value === null || value === undefined || value === '' ? '-' : value.toString();
  }

  formatPercent(value: number | undefined): string {
    return value === undefined ? '-' : `${value}%`;
  }

  formatBoolean(value: boolean | undefined): string {
    if (value === undefined) {
      return '-';
    }
    return value ? this.translate.translate('common.yes') : this.translate.translate('common.no');
  }
  formatNumber(value: number | undefined): string {
    console.log('formatNumber2decimal', value);
    if (value === undefined || value === null) {
      return '-';
    }
    return new Intl.NumberFormat('pl-PL', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value);
  }
  formatNumber2decimal(value: number | undefined): string {
    console.log('formatNumber2decimal', value);
    if (value === undefined || value === null) {
      return '-';
    }
    return new Intl.NumberFormat('pl-PL', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  }
  formatNumber3decimal(value: number | undefined): string {
    console.log('formatNumber2decimal', value);
    if (value === undefined || value === null) {
      return '-';
    }
    return new Intl.NumberFormat('pl-PL', {
      minimumFractionDigits: 3,
      maximumFractionDigits: 3,
    }).format(value);
  }
  formatNumber1decimal(value: number | undefined): string {
    console.log('formatNumber2decimal', value);
    if (value === undefined || value === null) {
      return '-';
    }
    return new Intl.NumberFormat('pl-PL', {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    }).format(value);
  }
  hasCollectionItems(value: unknown): boolean {
    if (value === null || value === undefined) {
      return false;
    }

    if (Array.isArray(value)) {
      return value.length > 0;
    }

    if (value instanceof Set || value instanceof Map) {
      return value.size > 0;
    }

    return false;
  }
}
