import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ThemeService } from '../../core/services/theme-service';
import { ThemeSwitcher } from './theme-switcher';

describe('ThemeSwitcher', () => {
  let component: ThemeSwitcher;
  let fixture: ComponentFixture<ThemeSwitcher>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ThemeSwitcher],
      providers: [{ provide: ThemeService, useValue: { toggleTheme: vi.fn() } }],
    })
    .compileComponents();

    fixture = TestBed.createComponent(ThemeSwitcher);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
