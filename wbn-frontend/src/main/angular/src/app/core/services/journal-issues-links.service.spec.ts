import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { ConfigurationService, JournalLinksDto } from '../backend';
import { JournalIssuesLinksService } from './journal-issues-links.service';

describe('JournalIssuesLinksService', () => {
  let response: Subject<JournalLinksDto>;
  let getJournalLinks: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    response = new Subject<JournalLinksDto>();
    getJournalLinks = vi.fn(() => response);
    TestBed.configureTestingModule({
      providers: [{ provide: ConfigurationService, useValue: { getJournalLinks } }],
    });
  });

  it('loads configuration once for all journals and updates links when the template arrives', () => {
    const links = TestBed.inject(JournalIssuesLinksService);
    expect(links.url(123)).toBeNull();
    response.next({
      issuesUrlTemplate: 'https://journals.example.test/issues?issueId=all&journalId={journalId}',
    });
    expect(links.url(123)).toBe('https://journals.example.test/issues?issueId=all&journalId=123');
    expect(TestBed.inject(JournalIssuesLinksService).url(456)).toBe(
      'https://journals.example.test/issues?issueId=all&journalId=456',
    );
    expect(getJournalLinks).toHaveBeenCalledTimes(1);
  });

  it.each([undefined, 0, -1, 1.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1])(
    'does not create a link for an invalid journal id: %s',
    (id) => {
      const links = TestBed.inject(JournalIssuesLinksService);
      response.next({ issuesUrlTemplate: 'https://journals.example.test/issues/{journalId}' });
      expect(links.url(id)).toBeNull();
    },
  );

  it('does not create a link when the template is empty', () => {
    const links = TestBed.inject(JournalIssuesLinksService);
    response.next({ issuesUrlTemplate: '' });
    expect(links.url(123)).toBeNull();
  });

  it('keeps links unavailable when configuration fails', () => {
    const links = TestBed.inject(JournalIssuesLinksService);
    expect(() => response.error(new Error('offline'))).not.toThrow();
    expect(links.url(123)).toBeNull();
  });
});
