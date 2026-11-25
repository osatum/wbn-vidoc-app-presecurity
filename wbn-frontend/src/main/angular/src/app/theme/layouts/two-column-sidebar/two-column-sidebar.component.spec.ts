import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { translocoTestingModule } from '../../../testing/transloco-testing-module';
import { TwoColumnSidebarComponent } from './two-column-sidebar.component';

describe('TwoColumnSidebarComponent', () => {
  let component: TwoColumnSidebarComponent;
  let fixture: ComponentFixture<TwoColumnSidebarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TwoColumnSidebarComponent, translocoTestingModule],
      providers: [provideRouter([])],
    })
    .overrideComponent(TwoColumnSidebarComponent, { set: { imports: [], template: '' } })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TwoColumnSidebarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
