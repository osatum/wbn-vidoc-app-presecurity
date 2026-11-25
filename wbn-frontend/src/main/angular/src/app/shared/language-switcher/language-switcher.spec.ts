import { ComponentFixture, TestBed } from '@angular/core/testing';

import { translocoTestingModule } from '../../testing/transloco-testing-module';
import { LanguageSwitcher } from './language-switcher';

describe('Translate', () => {
  let component: LanguageSwitcher;
  let fixture: ComponentFixture<LanguageSwitcher>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LanguageSwitcher, translocoTestingModule],
    })
    .compileComponents();

    fixture = TestBed.createComponent(LanguageSwitcher);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
