import { ComponentFixture, TestBed } from '@angular/core/testing';

import { translocoTestingModule } from '../../testing/transloco-testing-module';
import { PortalFooter } from './portal-footer';

describe('PortalFooter', () => {
  let component: PortalFooter;
  let fixture: ComponentFixture<PortalFooter>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PortalFooter, translocoTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(PortalFooter);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
