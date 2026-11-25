import {Component} from '@angular/core';
import {RouterOutlet} from "@angular/router";
import {BOOTSTRAP_LAYOUT_STYLE_GROUP, setActiveStylesheet,} from '../../core/feature/styles-loader';
import {ToastsContainer} from '../../shared/toasts-container/toasts-container';
import {DefaultHeader} from '../../shared/default-header/default-header';
import {DefaultFooter} from '../../shared/default-footer/default-footer';
import { PortalHeader } from '../../shared/portal-header/portal-header';
import { PortalFooter } from '../../shared/portal-footer/portal-footer';
import { CookieConsent } from '../../shared/cookie-consent/cookie-consent';

@Component({
  selector: 'app-institution-layout',
  imports: [
    RouterOutlet,
    ToastsContainer,
    DefaultHeader,
    DefaultFooter,
    PortalHeader,
    PortalFooter,
    CookieConsent,
  ],
  templateUrl: './institution-layout.html',
  styleUrl: './institution-layout.css',
})
export class InstitutionLayout {
  constructor() {
    setActiveStylesheet(
      '/assets/css/lazy/bootstrap-portal.css',
      'lazy-bootstrap-portal',
      BOOTSTRAP_LAYOUT_STYLE_GROUP,
    );
  }
}
