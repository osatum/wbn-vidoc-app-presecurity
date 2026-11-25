import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DefaultFooter } from './default-footer';

describe('DefaultFooter', () => {
  let component: DefaultFooter;
  let fixture: ComponentFixture<DefaultFooter>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DefaultFooter],
    }).compileComponents();

    fixture = TestBed.createComponent(DefaultFooter);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
