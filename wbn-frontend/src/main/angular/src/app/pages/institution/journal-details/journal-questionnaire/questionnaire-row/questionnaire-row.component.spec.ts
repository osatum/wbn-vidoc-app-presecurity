import { ComponentFixture, TestBed } from '@angular/core/testing';

import { QuestionnaireRowComponent } from './questionnaire-row.component';
import { translocoTestingModule } from '../../../../../testing/transloco-testing-module';

describe('QuestionnaireRowComponent', () => {
  let component: QuestionnaireRowComponent;
  let fixture: ComponentFixture<QuestionnaireRowComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QuestionnaireRowComponent, translocoTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(QuestionnaireRowComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('description', 'Czas publikacji');
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
