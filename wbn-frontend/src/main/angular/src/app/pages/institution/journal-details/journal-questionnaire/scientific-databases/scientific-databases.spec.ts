import { ComponentFixture, TestBed } from '@angular/core/testing';

import { translocoTestingModule } from '../../../../../testing/transloco-testing-module';
import { ScientificDatabases } from './scientific-databases';

describe('ScientificDatabases', () => {
  let component: ScientificDatabases;
  let fixture: ComponentFixture<ScientificDatabases>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScientificDatabases, translocoTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(ScientificDatabases);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
