import { Injectable } from '@angular/core';
import { CookieService } from 'ngx-cookie-service';
import { TranslocoService } from '@jsverse/transloco';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  constructor(
    public translate: TranslocoService,
    private cookieService: CookieService,
  ) {}

  /***
   * Cookie Language set
   */
  public setLanguage(lang: any) {
    this.translate.setActiveLang(lang);
    this.cookieService.set('lang', lang);
  }
}
