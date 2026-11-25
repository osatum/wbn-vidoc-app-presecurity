import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class ThemeService {
  private themeKey = 'app-theme';

  constructor() {
    this.loadTheme();
  }

  toggleTheme() {
    const current = document.documentElement.getAttribute('data-bs-theme');
    const next = current === 'dark' ? 'light' : 'dark';

    document.documentElement.setAttribute('data-bs-theme', next);
    localStorage.setItem(this.themeKey, next);
  }

  loadTheme() {
    const saved = localStorage.getItem(this.themeKey) || 'light';
    document.documentElement.setAttribute('data-bs-theme', saved);
  }
}
