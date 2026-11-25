import { JournalSearchMetadata } from './journalSearchMetadata';

export type JournalSearchCounts = Omit<JournalSearchMetadata, 'mniswPointClasses' | 'icvMin' | 'icvMax'>;
