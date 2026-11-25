import { inject, Injectable } from '@angular/core';
import { AvailableLangs, LangDefinition, TranslocoService } from '@jsverse/transloco';

type LanguageItem = { text: string; flag: string; lang: string };

const LANGUAGE_ITEM_MAP: Record<string, Omit<LanguageItem, 'lang'>> = {
  en: { text: 'English', flag: 'assets/images/flags/gb.svg' },
  pl: { text: 'Polski', flag: 'assets/images/flags/pl.svg' },
};

@Injectable({
  providedIn: 'root',
})
export class LanguageService {
  private readonly translocoService: TranslocoService = inject(TranslocoService);

  langChanges() {
    return this.translocoService.langChanges$;
  }

  getActiveLang(): LanguageItem {
    const currentLang = this.translocoService.getActiveLang();
    const lang = this.normalizeLang(currentLang);
    const languageItem = LANGUAGE_ITEM_MAP[lang];

    return {
      text: languageItem?.text ?? lang,
      flag: languageItem?.flag ?? `assets/images/flags/${lang}.svg`,
      lang,
    };
  }

  getLanguageItems(): LanguageItem[] {
    const availableLangs = this.translocoService.getAvailableLangs();
    const currentLang = this.translocoService.getActiveLang();

    return [...availableLangs]
      .sort((first, second) => {
        const firstLang = this.normalizeLang(first);
        const secondLang = this.normalizeLang(second);

        if (firstLang === currentLang) {
          return -1;
        }

        if (secondLang === currentLang) {
          return 1;
        }

        return 0;
      })
      .map((language) => {
        const lang = this.normalizeLang(language);
        const languageItem = LANGUAGE_ITEM_MAP[lang];

        return {
          text: languageItem?.text ?? lang,
          flag: languageItem?.flag ?? `assets/images/flags/${lang}.svg`,
          lang,
        };
      });
  }

  getAvailableLangs(): AvailableLangs {
    const availableLangs = this.translocoService.getAvailableLangs();
    const currentLang = this.translocoService.getActiveLang();

    // jeśli jest to tablica stringów
    if (Array.isArray(availableLangs) && typeof availableLangs[0] === 'string') {
      return (availableLangs as string[]).filter((lang) => lang !== currentLang);
    }

    // jeśli jest to tablica obiektów LangDefinition
    return (availableLangs as LangDefinition[]).filter((lang) => lang.id !== currentLang);
  }

  setLang(language: string | LangDefinition): void {
    const lang = typeof language === 'string' ? language : language.id;
    this.translocoService.setActiveLang(lang);
  }

  private normalizeLang(language: string | LangDefinition) {
    return typeof language === 'string' ? language : language.id;
  }
}
