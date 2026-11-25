import { Injectable, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { ConfigurationService } from '../backend';

@Injectable({ providedIn: 'root' })
export class JournalIssuesLinksService {
  private readonly links = toSignal(
    inject(ConfigurationService)
      .getJournalLinks()
      .pipe(catchError(() => of(null))),
  );

  url(journalId: number | undefined): string | null {
    const template = this.links()?.issuesUrlTemplate;
    return template && journalId != null && Number.isSafeInteger(journalId) && journalId > 0
      ? template.replace('{journalId}', String(journalId))
      : null;
  }
}
