import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AlertData } from './alert-data';

describe('AlerData', () => {
  let component: AlertData;
  let fixture: ComponentFixture<AlertData>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AlertData],
    }).compileComponents();

    fixture = TestBed.createComponent(AlertData);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
