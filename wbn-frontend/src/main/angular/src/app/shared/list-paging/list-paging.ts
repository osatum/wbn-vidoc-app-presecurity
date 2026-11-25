import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-list-paging',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './list-paging.html',
  styleUrl: './list-paging.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListPaging {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly totalElements = input.required<number>();
  readonly size = input.required<number>();
  readonly canGoPrevious = input.required<boolean>();
  readonly canGoNext = input.required<boolean>();
  readonly pageSizeOptions = input<readonly number[]>([5, 10, 25, 50]);
  readonly effectivePageSizeOptions = computed(() => {
    const size = this.size();
    const options = this.pageSizeOptions();

    return options.includes(size) ? options : [size, ...options];
  });

  readonly previousPage = output<void>();
  readonly nextPage = output<void>();
  readonly pageSizeChange = output<number>();

  onPageSizeChange(size: number): void {
    this.pageSizeChange.emit(size);
  }
}
