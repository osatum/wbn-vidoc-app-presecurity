import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { translocoTestingModule } from '../../../testing/transloco-testing-module';
import { Denied } from './denied';

describe('Danied', () => {
  let component: Denied;
  let fixture: ComponentFixture<Denied>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Denied, translocoTestingModule],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Denied);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
