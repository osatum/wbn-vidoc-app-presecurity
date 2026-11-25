import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Store } from '@ngrx/store';

import { createLayoutStoreMock } from '../../../testing/layout-store-testing';
import { RightsidebarComponent } from './rightsidebar.component';

describe('RightsidebarComponent', () => {
  let component: RightsidebarComponent;
  let fixture: ComponentFixture<RightsidebarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RightsidebarComponent],
      providers: [{ provide: Store, useFactory: createLayoutStoreMock }],
    })
    .overrideComponent(RightsidebarComponent, { set: { imports: [], template: '' } })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(RightsidebarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
