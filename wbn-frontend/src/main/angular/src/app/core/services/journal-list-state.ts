import { JournalListQuery, SearchView } from '../backend';

export const JOURNAL_PAGE_SIZES = [10, 25, 50, 100] as const;

export interface FeeCriteria {
  otherFeesFreeOnly: boolean;
  openAccessFeeFreeOnly: boolean;
  openAccessFeeMaxPln: string;
  publicationFeeFreeOnly: boolean;
  publicationFeeMaxPln: string;
  rapidReviewFeeFreeOnly: boolean;
  rapidReviewFeeMaxPln: string;
  submissionFeeFreeOnly: boolean;
  submissionFeeMaxPln: string;
}

export interface NonFeeCriteria {
  doiOnly: boolean;
  editorialSystemOnly: boolean;
  newsletterOnly: boolean;
  socialMediaOnly: boolean;
  antiPlagiarismOnly: boolean;
  aopOnly: boolean;
  repositoryIds: string[];
  licenseCodes: string[];
  accessModels: string[];
  frequencyCodes: string[];
  reviewModels: string[];
  firstIssueYearFrom: string;
  firstIssueYearTo: string;
}

export const EMPTY_SURVEY_FILTERS = {
  reviewDaysFrom: '',
  reviewDaysTo: '',
  initialAssessmentDaysFrom: '',
  initialAssessmentDaysTo: '',
  publicationDaysFrom: '',
  publicationDaysTo: '',
  rejectionRateFrom: '',
  rejectionRateTo: '',
  authorForeignAffiliationPercentageFrom: '',
  authorForeignAffiliationPercentageTo: '',
  reviewerForeignAffiliationPercentageFrom: '',
  reviewerForeignAffiliationPercentageTo: '',
};
export type SurveyFilterName = keyof typeof EMPTY_SURVEY_FILTERS;
export type JournalFilters = typeof EMPTY_SURVEY_FILTERS & {
  nonFeeFilters: NonFeeCriteria;
  feeFilters: FeeCriteria;
  journalTitle: string;
  issn: string;
  publisher: string;
  countryCodes: string[];
  languageCodes: string[];
  disciplineCodes: string[];
  sortBy: JournalListQuery.SortByEnum;
  ratingStatus: JournalListQuery.RatingStatusEnum | '';
  icvFrom: string;
  icvTo: string;
  mniswOnly: boolean;
  mniswDisciplineCodes: string[];
  mniswPointsFrom: string;
  mniswPointsTo: string;
};

export interface JournalListNavigationState {
  surveyYear?: number;
  searchView?: SearchView;
  analytics?: JournalSearchAnalyticsState;
  applied: JournalFilters;
  draft: JournalFilters;
  page: number;
  size?: number;
  scroll: [number, number];
  filterScroll?: number;
  expandedDetails: number[];
  showDefaultNotice: boolean;
}

export interface JournalSearchAnalyticsState {
  operationId: string;
  origin: 'INITIAL' | 'RETURN' | 'SEARCH' | 'CLEAR';
  emptyReported: boolean;
  attempt: number;
}

export function defaultJournalListQuery(searchView: SearchView = 'ADVANCED'): JournalListQuery {
  return {
    countryCodes: searchView === 'BASIC' ? [] : ['pl'],
    sortBy: JournalListQuery.SortByEnum.SortByTitleAsc,
    page: 0,
    size: 10,
    first: 0,
  };
}
