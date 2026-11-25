import { ComponentFixture, TestBed } from '@angular/core/testing';

import { translocoTestingModule } from '../../../testing/transloco-testing-module';
import { InstitutionData } from './institution-data';

describe('InstitutionData', () => {
  let component: InstitutionData;
  let fixture: ComponentFixture<InstitutionData>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InstitutionData, translocoTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(InstitutionData);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
