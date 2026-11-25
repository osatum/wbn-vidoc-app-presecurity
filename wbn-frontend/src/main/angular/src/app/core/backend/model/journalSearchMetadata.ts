export interface JournalSearchMetadata { 
    mniswPointClasses?: Array<number>;
    icvMin?: number;
    icvMax?: number;
    countryCounts?: { [key: string]: number; };
    languageCounts?: { [key: string]: number; };
    disciplineCounts?: { [key: string]: number; };
    repositoryCounts?: { [key: string]: number; };
    licenseCounts?: { [key: string]: number; };
    accessModelCounts?: { [key: string]: number; };
    frequencyCounts?: { [key: string]: number; };
    reviewModelCounts?: { [key: string]: number; };
    featureCounts?: { [key: string]: number; };
    mniswDisciplineCounts?: { [key: string]: number; };
}
