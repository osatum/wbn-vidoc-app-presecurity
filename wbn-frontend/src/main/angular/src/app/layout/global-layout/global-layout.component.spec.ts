import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GlobalLayoutComponent } from './global-layout.component';
import { translocoTestingModule } from '../../testing/transloco-testing-module';
import { CookieService } from 'ngx-cookie-service';

describe('GlobalLayout', () => {
  let component: GlobalLayoutComponent;
  let fixture: ComponentFixture<GlobalLayoutComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GlobalLayoutComponent, translocoTestingModule],
      providers: [{ provide: CookieService, useValue: { get: () => 'accepted' } }],
    }).compileComponents();

    fixture = TestBed.createComponent(GlobalLayoutComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
