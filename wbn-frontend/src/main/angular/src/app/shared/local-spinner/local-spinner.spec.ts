import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LocalSpinner } from './local-spinner';

describe('LocalSpinner', () => {
  let component: LocalSpinner;
  let fixture: ComponentFixture<LocalSpinner>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LocalSpinner]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LocalSpinner);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
