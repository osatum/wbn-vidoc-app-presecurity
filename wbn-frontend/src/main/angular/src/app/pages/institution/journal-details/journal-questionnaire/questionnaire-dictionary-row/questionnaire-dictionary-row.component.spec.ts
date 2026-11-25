import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JournalNameService } from '../../../../../core/services/journal-name-service';
import { QuestionnaireDictionaryRowComponent } from './questionnaire-dictionary-row.component';

@Component({
  imports: [QuestionnaireDictionaryRowComponent],
  template: `
    <app-questionnaire-dictionary-row
      description="Opis wiersza"
      [values]="values"
      [highlight]="true"
    />
  `,
})
class TestHostComponent {
  values: readonly unknown[] | ReadonlySet<unknown> | null | undefined = ['first', 'second'];
}

describe('QuestionnaireDictionaryRowComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
      providers: [
        {
          provide: JournalNameService,
          useValue: {
            getJournalDictionaryItem: (item: unknown) => `label:${String(item)}`,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
  });

  it('should render description and dictionary values', () => {
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('.header-custom')?.textContent?.trim()).toBe('Opis wiersza');
    expect(element.textContent).toContain('label:first');
    expect(element.textContent).toContain('label:second');
    expect(element.querySelector('.bg-light-subtle')).not.toBeNull();
  });

  it('should render dash when values are empty', () => {
    fixture.componentInstance.values = [];
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;

    expect(element.textContent).toContain('-');
  });
});
