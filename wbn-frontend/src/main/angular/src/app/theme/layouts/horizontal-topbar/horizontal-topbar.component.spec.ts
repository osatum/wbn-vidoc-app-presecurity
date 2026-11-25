import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { translocoTestingModule } from '../../../testing/transloco-testing-module';
import { HorizontalTopbarComponent } from './horizontal-topbar.component';

describe('HorizontalTopbarComponent', () => {
  let component: HorizontalTopbarComponent;
  let fixture: ComponentFixture<HorizontalTopbarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HorizontalTopbarComponent, translocoTestingModule],
      providers: [provideRouter([])],
    })
    .overrideComponent(HorizontalTopbarComponent, { set: { imports: [], template: '' } })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(HorizontalTopbarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
