import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Store } from '@ngrx/store';

import { createLayoutStoreMock } from '../../../testing/layout-store-testing';
import { VerticalComponent } from './vertical.component';

describe('VerticalComponent', () => {
  let component: VerticalComponent;
  let fixture: ComponentFixture<VerticalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VerticalComponent],
      providers: [provideRouter([]), { provide: Store, useFactory: createLayoutStoreMock }],
    })
    .overrideComponent(VerticalComponent, { set: { imports: [], template: '' } })
    .compileComponents();
  });

  beforeEach(() => {
    vi.useFakeTimers();
    const preloader = document.createElement('div');
    preloader.id = 'preloader';
    document.body.appendChild(preloader);

    fixture = TestBed.createComponent(VerticalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    vi.runOnlyPendingTimers();
  });

  afterEach(() => {
    document.getElementById('preloader')?.remove();
    vi.useRealTimers();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
