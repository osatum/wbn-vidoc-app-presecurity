import { Component, inject, Input, OnChanges } from '@angular/core';
import { ArticlePublished, StabilityDto } from '../../../../../core/backend';
import { JournalNameService } from '../../../../../core/services/journal-name-service';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-stability',
  imports: [TranslocoPipe],
  templateUrl: './stability.html',
  styleUrl: './stability.css',
})
export class Stability implements OnChanges {
  @Input() stability?: StabilityDto;
  @Input() year?: number;
  protected readonly journalNameService = inject(JournalNameService);
  protected articlesByYear: ArticlePublished[] = [];

  ngOnChanges(): void {
    this.articlesByYear = [...(this.stability?.articlePublished ?? [])].sort(
      (left, right) =>
        (left.year ?? Number.MAX_SAFE_INTEGER) - (right.year ?? Number.MAX_SAFE_INTEGER),
    );
  }
}
