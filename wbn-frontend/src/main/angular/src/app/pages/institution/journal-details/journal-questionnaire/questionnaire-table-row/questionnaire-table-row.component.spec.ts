import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { QuestionnaireTableRowComponent } from './questionnaire-table-row.component';

@Component({
  imports: [QuestionnaireTableRowComponent],
  template: `
    <table>
      <tbody>
        <tr appQuestionnaireTableRow description="Opis wiersza">
          <span class="projected-content">Dowolna wartość</span>
        </tr>
      </tbody>
    </table>
  `,
})
class TestHostComponent {}

describe('QuestionnaireTableRowComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
  });

  it('should render description and projected content', () => {
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('td.description')?.textContent?.trim()).toBe('Opis wiersza');
    expect(element.querySelector('.projected-content')?.textContent?.trim()).toBe('Dowolna wartość');
  });
});
