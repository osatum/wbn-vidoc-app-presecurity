import { JournalFeeView } from './journalFeeView';


export interface JournalFeesView {
    noFees?: boolean;
    items?: Array<JournalFeeView>;
}
