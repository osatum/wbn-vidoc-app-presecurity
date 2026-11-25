import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { DecisionFeeView } from '../../../core/services/journal-decision-annual';

export function decisionFee(overrides: Partial<DecisionFeeView> = {}): DecisionFeeView {
  return {
    category: 'SUBMISSION',
    type: 'NO_FEE',
    status: 'AVAILABLE',
    charged: false,
    minimum: null,
    maximum: null,
    currency: null,
    selectionStatus: 'RESOLVED',
    effectiveVariant: null,
    sourceRanges: [],
    exchangeRates: [],
    variants: [],
    ...overrides,
  };
}

export function decisionFeeCard(): JournalDecisionView {
  const missing = { status: 'MISSING', value: null } as const;
  const unavailable = { status: 'UNAVAILABLE', value: null } as const;
  const categories = ['SUBMISSION', 'PUBLICATION', 'OPEN_ACCESS', 'RAPID_REVIEW', 'OTHER'] as const;
  return {
    surveyYear: 2024,
    annualStatus: 'AVAILABLE',
    availableSurveyYears: [2024, 2025],
    identity: { id: 123 },
    icv: 81.125,
    icvStatus: 'AVAILABLE',
    mnisw: null,
    icvHistory: [],
    annual: {
      surveyYear: 2024,
      times: { initialAssessment: missing, review: missing, publication: missing },
      process: {
        firstIssueYear: missing,
        continuedYear: missing,
        frequency: missing,
        aheadOfPrint: missing,
        rejectionRate: missing,
        publishingBreak: missing,
      },
      international: { authors: missing, reviewers: missing, scientificCouncil: missing },
      standards: {
        antiPlagiarism: missing,
        publicCriteria: missing,
        editorialSystem: missing,
        reviewing: { models: [], none: null, status: 'MISSING' },
        access: missing,
        accessConditions: missing,
        licence: missing,
        socialMedia: missing,
      },
      profile: {
        mainGroup: missing,
        memberships: missing,
        scientificCategories: missing,
        wosCategories: missing,
        scopusCategories: missing,
      },
      indexing: missing,
      fees: categories.map((category) => decisionFee({ category })),
    },
    benchmarksVisible: true,
    benchmarks: {
      surveyYear: 2024,
      importId: 'build-1',
      groupId: 1008,
      icv: unavailable,
      times: { initialAssessment: unavailable, review: unavailable, publication: unavailable },
      rejectionRate: unavailable,
      international: {
        authors: unavailable,
        reviewers: unavailable,
        scientificCouncil: unavailable,
      },
      fees: categories.map((category, i) => ({
        category,
        comparison: { status: 'AVAILABLE', value: { chargedPercent: [0.3, 50, 99.8, 100, 0][i] } },
      })),
    },
  };
}
