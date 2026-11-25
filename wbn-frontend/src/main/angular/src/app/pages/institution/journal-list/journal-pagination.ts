import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { JOURNAL_PAGE_SIZES } from '../../../core/services/journal-list-state';

@Component({
  selector: 'app-journal-pagination',
  imports: [TranslocoPipe],
  templateUrl: './journal-pagination.html',
  styleUrl: './journal-pagination.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalPagination {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly totalElements = input.required<number>();
  readonly pageSize = input(10);
  readonly pageSizeOptions = JOURNAL_PAGE_SIZES;
  readonly disabled = input(false);
  readonly pageChange = output<number>();
  readonly pageSizeChange = output<number>();

  readonly pages = computed(() => {
    const total = this.totalPages();
    if (total <= 0) {
      return [];
    }
    const current = Math.min(Math.max(this.page(), 0), total - 1);
    const selected = new Set([0, total - 1]);
    const start = Math.max(0, Math.min(current - 2, total - 5));
    for (let index = start; index < Math.min(total, start + 5); index++) {
      selected.add(index);
    }
    const pages: { page: number; gap: boolean }[] = [];
    [...selected]
      .sort((first, second) => first - second)
      .forEach((page) => {
        const previous = pages.at(-1)?.page;
        pages.push({ page, gap: previous !== undefined && page > previous + 1 });
      });
    return pages;
  });

  select(page: number): void {
    if (
      !this.disabled() &&
      Number.isSafeInteger(page) &&
      page >= 0 &&
      page < this.totalPages() &&
      page !== this.page()
    ) {
      this.pageChange.emit(page);
    }
  }

  jump(value: string): void {
    if (/^\d+$/.test(value.trim())) {
      this.select(Number(value) - 1);
    }
  }

  selectPageSize(value: string): void {
    const size = Number(value);
    if (
      !this.disabled() &&
      size !== this.pageSize() &&
      this.pageSizeOptions.some((allowed) => allowed === size)
    ) {
      this.pageSizeChange.emit(size);
    }
  }
}
