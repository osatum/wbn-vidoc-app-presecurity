import { ComponentFixture, TestBed } from '@angular/core/testing';

import { translocoTestingModule } from '../../../../../testing/transloco-testing-module';
import { Digitalization } from './digitalization';

describe('Digitalization', () => {
  let component: Digitalization;
  let fixture: ComponentFixture<Digitalization>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Digitalization, translocoTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(Digitalization);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
