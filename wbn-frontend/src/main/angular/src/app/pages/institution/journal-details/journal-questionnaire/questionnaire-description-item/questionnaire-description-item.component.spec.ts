import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { QuestionnaireDescriptionItemComponent } from './questionnaire-description-item.component';

@Component({
  imports: [QuestionnaireDescriptionItemComponent],
  template: `
    <dl class="row">
      <app-questionnaire-description-item label="Etykieta">
        <span class="projected-content">Dowolna wartość</span>
      </app-questionnaire-description-item>
    </dl>
  `,
})
class TestHostComponent {}

describe('QuestionnaireDescriptionItemComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
  });

  it('should render label and projected content', () => {
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('dt')?.textContent?.trim()).toBe('Etykieta');
    expect(element.querySelector('.projected-content')?.textContent?.trim()).toBe('Dowolna wartość');
  });
});
