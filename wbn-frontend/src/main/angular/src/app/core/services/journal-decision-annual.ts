import { MniswDisciplineDictionaryView, PaidArticleView } from '../backend';

export type DecisionValueState =
  | 'AVAILABLE'
  | 'MISSING'
  | 'INVALID'
  | 'UNRESOLVED'
  | 'RATES_UNAVAILABLE';
export interface DecisionValue<T> {
  status: DecisionValueState;
  value: T | null;
}
export interface DecisionGroup {
  id: number;
  namePl: string | null;
  nameEn: string | null;
  nameRu: string | null;
}
export interface DecisionCategory {
  id: number | null;
  code: string | null;
  main: boolean | null;
  namePl: string | null;
  nameEn: string | null;
  nameRu: string | null;
}
export interface MniswRatingView {
  year: number;
  listed: boolean | null;
  points: number | null;
  disciplines: MniswDisciplineDictionaryView[];
  status: DecisionValueState;
}
export interface DecisionExchangeRate {
  currency: string;
  mid: number;
  tableType: 'A' | 'B';
  tableNumber: string;
  effectiveDate: string;
}
export interface DecisionFeeView {
  category: 'SUBMISSION' | 'PUBLICATION' | 'OPEN_ACCESS' | 'RAPID_REVIEW' | 'OTHER';
  type: 'NO_FEE' | 'ONE_FEE' | 'VARIOUS_FEE' | 'COUNTRY_SPECIFIC_FEE' | null;
  status: DecisionValueState;
  charged: boolean | null;
  minimum: number | null;
  maximum: number | null;
  currency: string | null;
  selectionStatus: 'RESOLVED' | 'LEGACY' | 'UNRESOLVED' | 'INCOMPLETE' | 'INVALID' | null;
  effectiveVariant: 'INTERNAL_AUTHORS' | 'EXTERNAL_AUTHORS' | null;
  sourceRanges: { minimum: number; maximum: number; currency: string | null }[];
  exchangeRates: DecisionExchangeRate[];
  variants: DecisionFeeVariant[];
}

export interface DecisionFeeVariant {
  variant: 'INTERNAL_AUTHORS' | 'EXTERNAL_AUTHORS';
  status: DecisionValueState;
  charged: boolean | null;
  minimum: number | null;
  maximum: number | null;
  currency: string | null;
}
export interface JournalDecisionAnnual {
  surveyYear: number;
  times: {
    initialAssessment: DecisionValue<number>;
    review: DecisionValue<number>;
    publication: DecisionValue<number>;
  };
  process: {
    firstIssueYear: DecisionValue<number>;
    continuedYear: DecisionValue<number>;
    frequency: DecisionValue<'c' | 'b' | 'f' | 'g' | 'e' | 'q' | 'm' | 'a' | 'k' | 't'>;
    aheadOfPrint: DecisionValue<boolean>;
    rejectionRate: DecisionValue<number>;
    publishingBreak: DecisionValue<boolean>;
  };
  international: {
    authors: DecisionValue<number>;
    reviewers: DecisionValue<number>;
    scientificCouncil: DecisionValue<number>;
  };
  standards: {
    antiPlagiarism: DecisionValue<'INDICATED' | 'NONE' | 'NOT_INDICATED'>;
    publicCriteria: DecisionValue<'INDICATED' | 'NONE' | 'NOT_INDICATED'>;
    editorialSystem: DecisionValue<'INDICATED' | 'NONE' | 'NOT_INDICATED'>;
    reviewing: {
      models: {
        code: 'DOUBLE_BLIND_REVIEW' | 'SINGLE_BLIND_REVIEW' | 'OPEN_PEER_REVIEW';
        confirmed: boolean | null;
        share: DecisionValue<number>;
      }[];
      none: boolean | null;
      status: DecisionValueState;
    };
    access: DecisionValue<PaidArticleView.PaidTypeEnum>;
    accessConditions: DecisionValue<string[]>;
    licence: DecisionValue<
      | 'NONE'
      | 'CC_BY'
      | 'CC_BY_SA'
      | 'CC_BY_ND'
      | 'CC_BY_NC'
      | 'CC_BY_NC_SA'
      | 'CC_BY_NC_ND'
      | 'AUTHOR_PREFERENCE'
    >;
    socialMedia: DecisionValue<{
      used: boolean | null;
      channels: ('FACEBOOK' | 'INSTAGRAM' | 'LINKEDIN' | 'X' | 'YOUTUBE')[];
      other: string | null;
    }>;
  };
  profile: {
    mainGroup: DecisionValue<DecisionGroup>;
    memberships: DecisionValue<DecisionGroup[]>;
    scientificCategories: DecisionValue<DecisionCategory[]>;
    wosCategories: DecisionValue<DecisionCategory[]>;
    scopusCategories: DecisionValue<DecisionCategory[]>;
  };
  indexing: DecisionValue<{ id: number | null; name: string }[]>;
  fees: DecisionFeeView[];
}
