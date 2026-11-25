import { ComponentFixture, TestBed } from '@angular/core/testing';

import { translocoTestingModule } from '../../../../../testing/transloco-testing-module';
import { SupplementaryData } from './supplementary-data';

describe('SupplementaryData', () => {
  let component: SupplementaryData;
  let fixture: ComponentFixture<SupplementaryData>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SupplementaryData, translocoTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(SupplementaryData);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
