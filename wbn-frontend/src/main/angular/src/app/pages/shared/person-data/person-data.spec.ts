import { ComponentFixture, TestBed } from '@angular/core/testing';

import { translocoTestingModule } from '../../../testing/transloco-testing-module';
import { PersonData } from './person-data';

describe('PersonData', () => {
  let component: PersonData;
  let fixture: ComponentFixture<PersonData>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PersonData, translocoTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(PersonData);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
