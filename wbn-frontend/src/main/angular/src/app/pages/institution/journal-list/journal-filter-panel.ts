import {
  ChangeDetectionStrategy,
  Component,
  input,
  TemplateRef,
  ViewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';

@Component({
  selector: 'app-journal-filter-panel',
  imports: [NgTemplateOutlet],
  template: `
    @if (!compact()) {
      <ng-container [ngTemplateOutlet]="content" />
    }
    <ng-template #content><ng-content /></ng-template>
  `,
  styles: ':host { display: contents; }',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalFilterPanel {
  readonly compact = input(false);
  @ViewChild('content', { static: true }) readonly content!: TemplateRef<unknown>;
}
