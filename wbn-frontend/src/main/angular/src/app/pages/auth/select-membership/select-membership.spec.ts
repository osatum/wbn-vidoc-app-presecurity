import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SelectMembership } from './select-membership';

describe('SelectMembership', () => {
  let component: SelectMembership;
  let fixture: ComponentFixture<SelectMembership>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SelectMembership]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SelectMembership);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
