export type OperatingProfile = 'BASIC' | 'ADVANCED' | 'BOTH';
export type SearchView = 'BASIC' | 'ADVANCED';
export type SystemFeature = 'BASIC_SEARCH' | 'ADVANCED_SEARCH' | 'DECISION_CARD';

export interface OperatingContextDto {
  institutionId: number;
  configuredProfile: OperatingProfile | null;
  defaultProfile: OperatingProfile;
  effectiveProfile: OperatingProfile;
  availableFeatures: SystemFeature[];
  defaultSearchView: SearchView;
}
