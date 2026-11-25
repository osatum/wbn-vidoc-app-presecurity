import {Component, inject} from '@angular/core';
import {AvailableLangs, LangDefinition, TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {
  NgbDropdown,
  NgbDropdownButtonItem,
  NgbDropdownItem,
  NgbDropdownMenu,
  NgbDropdownToggle
} from '@ng-bootstrap/ng-bootstrap';

const COUNTRY_CODE_MAP: Record<string, string> = {
  en: 'gb',
  an: 'gb',
  uk: 'gb',
};

@Component({
  selector: 'app-translate',
  imports: [
    NgbDropdown,
    NgbDropdownButtonItem,
    NgbDropdownItem,
    NgbDropdownMenu,
    NgbDropdownToggle,
    TranslocoDirective
  ],
  templateUrl: './language-switcher.html',
  styleUrl: './language-switcher.css',
})
export class LanguageSwitcher {
  private translocoService: TranslocoService = inject(TranslocoService);

  normalizeCountry(language: string | LangDefinition): string {
    const lang = this.normalizeLang(language);
    return COUNTRY_CODE_MAP[lang.toLowerCase()] ?? lang.toLowerCase();
  }

  trackByLang(language: string | LangDefinition): string {
    return this.normalizeLang(language);
  }

  getActiveLang() {
    return  this.translocoService.getActiveLang();
  }

  getAvailableLangs(): AvailableLangs {
    const availableLangs = this.translocoService.getAvailableLangs();
    const currentLang = this.translocoService.getActiveLang();

    // jeśli jest to tablica stringów
    if (Array.isArray(availableLangs) && typeof availableLangs[0] === 'string') {
      return (availableLangs as string[]).filter(lang => lang !== currentLang);
    }

    // jeśli jest to tablica obiektów LangDefinition
    return (availableLangs as LangDefinition[]).filter(lang => lang.id !== currentLang);
  }

  setLang(language: string | LangDefinition): void {
    const lang =
      typeof language === 'string'
        ? language
        : language.id;

    this.translocoService.setActiveLang(lang);
  }

  private normalizeLang(language: string | LangDefinition){
    return typeof language === 'string' ? language : language.id;
  }
}
