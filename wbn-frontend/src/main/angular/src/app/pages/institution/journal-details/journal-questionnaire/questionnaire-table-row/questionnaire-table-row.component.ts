import { Component, input } from '@angular/core';

@Component({
  selector: 'tr[appQuestionnaireTableRow]',
  template: `
    <td class="{{ this.findWidth() }} {{ this.findBg() }} description">
      {{ description() }}
      <ng-content select="[descriptionDetails]"></ng-content>
    </td>
    <td class="{{ this.findBg() }}">
      <ng-content></ng-content>
    </td>
  `,
})
export class QuestionnaireTableRowComponent {
  readonly description = input.required<string>();
  readonly widthClass = input<string>();
  readonly bgClass = input<string>();

  findWidth(): string {
    return this.widthClass() ?? 'w-75';
  }

  findBg(): string {
    return this.bgClass() ?? '';
  }
}
