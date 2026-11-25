import { DecisionFeeView } from './journal-decision-annual';

export interface DecisionComparison<T> {
  status: 'AVAILABLE' | 'UNAVAILABLE';
  value: T | null;
}
export interface DecisionQuartiles {
  q1: number;
  q3: number;
}
export interface DecisionTimeBenchmark extends DecisionQuartiles {
  axis: { minimum: number; maximum: number };
}
export interface JournalDecisionBenchmarks {
  surveyYear: number;
  importId: string;
  groupId: number | null;
  icv: DecisionComparison<{ lowerPercent: number; equalPercent: number; allEqual: boolean }>;
  times: {
    initialAssessment: DecisionComparison<DecisionTimeBenchmark>;
    review: DecisionComparison<DecisionTimeBenchmark>;
    publication: DecisionComparison<DecisionTimeBenchmark>;
  };
  rejectionRate: DecisionComparison<DecisionQuartiles>;
  international: {
    authors: DecisionComparison<{ median: number }>;
    reviewers: DecisionComparison<{ median: number }>;
    scientificCouncil: DecisionComparison<{ median: number }>;
  };
  fees: {
    category: DecisionFeeView['category'];
    comparison: DecisionComparison<{ chargedPercent: number }>;
  }[];
}
