import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ListPaging } from './list-paging';

describe('ListPaging', () => {
  let component: ListPaging;
  let fixture: ComponentFixture<ListPaging>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListPaging],
    }).compileComponents();

    fixture = TestBed.createComponent(ListPaging);
    fixture.componentRef.setInput('page', 0);
    fixture.componentRef.setInput('totalPages', 1);
    fixture.componentRef.setInput('totalElements', 0);
    fixture.componentRef.setInput('size', 10);
    fixture.componentRef.setInput('canGoPrevious', false);
    fixture.componentRef.setInput('canGoNext', false);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
