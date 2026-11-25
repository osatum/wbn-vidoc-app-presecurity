export interface JournalFeeView {
    category?: JournalFeeView.CategoryEnum;
    amount?: number;
    currencyCode?: string;
}
export namespace JournalFeeView {
    export type CategoryEnum = 'OTHER' | 'OPEN_ACCESS' | 'PUBLICATION' | 'RAPID_REVIEW' | 'SUBMISSION';
    export const CategoryEnum = {
        Other: 'OTHER' as CategoryEnum,
        OpenAccess: 'OPEN_ACCESS' as CategoryEnum,
        Publication: 'PUBLICATION' as CategoryEnum,
        RapidReview: 'RAPID_REVIEW' as CategoryEnum,
        Submission: 'SUBMISSION' as CategoryEnum
    };
}
