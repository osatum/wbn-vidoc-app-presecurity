import { ComponentFixture, TestBed } from '@angular/core/testing';

import { translocoTestingModule } from '../../../../../testing/transloco-testing-module';
import { PublishingVersions } from './publishing-versions';

describe('PublishingVersions', () => {
  let component: PublishingVersions;
  let fixture: ComponentFixture<PublishingVersions>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PublishingVersions, translocoTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(PublishingVersions);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
