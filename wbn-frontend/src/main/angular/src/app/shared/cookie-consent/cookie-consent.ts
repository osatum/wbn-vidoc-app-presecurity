import { AfterViewInit, Component, TemplateRef, ViewChild, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { NgbOffcanvas, NgbOffcanvasRef } from '@ng-bootstrap/ng-bootstrap';
import { CookieService } from 'ngx-cookie-service';

const COOKIE_CONSENT_NAME = 'jml_cookie_consent';
const COOKIE_CONSENT_VALUE = 'accepted';
const COOKIE_CONSENT_EXPIRATION_DAYS = 365;

@Component({
  selector: 'app-cookie-consent',
  imports: [TranslocoPipe],
  templateUrl: './cookie-consent.html',
  styleUrl: './cookie-consent.css',
})
export class CookieConsent implements AfterViewInit {
  private readonly cookies = inject(CookieService);
  private readonly offcanvasService = inject(NgbOffcanvas);
  private offcanvasRef?: NgbOffcanvasRef;

  @ViewChild('cookieConsentContent')
  private cookieConsentContent?: TemplateRef<unknown>;

  ngAfterViewInit(): void {
    const content = this.cookieConsentContent;

    if (this.hasAcceptedConsent() || !content) {
      return;
    }

    queueMicrotask(() => this.openConsentOffcanvas(content));
  }

  accept(): void {
    this.cookies.set(COOKIE_CONSENT_NAME, COOKIE_CONSENT_VALUE, {
      expires: COOKIE_CONSENT_EXPIRATION_DAYS,
      path: '/',
      sameSite: 'Lax',
    });
    this.offcanvasRef?.close();
  }

  private hasAcceptedConsent(): boolean {
    return this.cookies.get(COOKIE_CONSENT_NAME) === COOKIE_CONSENT_VALUE;
  }

  private openConsentOffcanvas(content: TemplateRef<unknown>): void {
    this.offcanvasRef = this.offcanvasService.open(content, {
      position: 'bottom',
      backdrop: false,
      keyboard: false,
      scroll: true,
      panelClass: 'cookie-consent-offcanvas',
    });
  }
}
