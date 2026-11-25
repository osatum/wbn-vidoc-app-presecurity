import {Component, inject} from '@angular/core';
import {ThemeService} from '../../core/services/theme-service';

@Component({
  selector: 'app-theme-switcher',
  imports: [],
  templateUrl: './theme-switcher.html',
  styleUrl: './theme-switcher.css',
})
export class ThemeSwitcher {

  themeService: ThemeService = inject(ThemeService);

  constructor() {
  }

  toggleTheme() {
    this.themeService.toggleTheme();
  }
}
