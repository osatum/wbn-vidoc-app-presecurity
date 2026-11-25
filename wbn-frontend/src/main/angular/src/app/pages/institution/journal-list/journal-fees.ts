import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { JournalFeesView } from '../../../core/backend';

@Component({
  selector: 'app-journal-fees',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    <div class="journal-result-terms">
      @if (publicationDays() != null) {
        <div class="journal-result-term" data-testid="journal-result-publication-time">
          <i class="ri-time-line" aria-hidden="true"></i>
          <div>
            <span class="header-custom"
              >{{ 'page.institution.journalList.publicationTime' | transloco }}:</span
            >
            <p class="journal-result-term-value value-custom">
              {{ publicationDays() }} {{ 'page.institution.journalList.daysUnit' | transloco }}
            </p>
          </div>
        </div>
      }
      <div class="journal-result-term" data-testid="journal-result-fees">
        <i class="ri-coins-line" aria-hidden="true"></i>
        <div>
          <span class="header-custom"
            >{{ 'page.institution.journalList.feesLabel' | transloco }}:</span
          >
          @if (fees()?.noFees) {
            <p class="journal-result-term-value value-custom">
              {{ 'page.institution.journalList.noFees' | transloco }}
            </p>
          } @else {
            @for (fee of fees()?.items ?? []; track fee.category) {
              <p class="journal-result-term-value value-custom">
                <span class="header-custom"
                  >{{ 'page.institution.journalList.feeLabels.' + fee.category | transloco }}:</span
                >
                @if (fee.category === 'OTHER') {
                  {{ 'page.institution.journalList.feePresent' | transloco }}
                } @else if (fee.amount != null && fee.currencyCode) {
                  {{ amountFormatter().format(fee.amount) }} {{ fee.currencyCode }}
                }
              </p>
            }
          }
        </div>
      </div>
    </div>
  `,
  styleUrl: './journal-fees.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalFees {
  readonly fees = input<JournalFeesView>();
  readonly publicationDays = input<number>();
  private readonly transloco = inject(TranslocoService);
  private readonly activeLanguage = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  readonly amountFormatter = computed(
    () =>
      new Intl.NumberFormat(this.activeLanguage(), {
        maximumFractionDigits: 12,
      }),
  );
}
