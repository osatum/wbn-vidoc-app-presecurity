import { Component, signal, computed, inject } from '@angular/core';
import {Router, ActivatedRoute, RouterOutlet} from '@angular/router';
import {ToastsContainer} from '../../shared/toasts-container/toasts-container';
import {
  BOOTSTRAP_LAYOUT_STYLE_GROUP,
  setActiveStylesheet,
} from '../../core/feature/styles-loader';
import {DefaultFooter} from '../../shared/default-footer/default-footer';
import { PortalFooter } from '../../shared/portal-footer/portal-footer';
import { PortalHeader } from '../../shared/portal-header/portal-header';
import { DefaultHeader } from '../../shared/default-header/default-header';
import { CookieConsent } from '../../shared/cookie-consent/cookie-consent';

@Component({
  selector: 'app-auth-layout',
  templateUrl: './auth-layout.html',
  imports: [
    RouterOutlet,
    ToastsContainer,
    DefaultFooter,
    PortalFooter,
    PortalHeader,
    DefaultHeader,
    CookieConsent,
  ],
})
export class AuthLayout {
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  subtitle = computed(() => {
    let r = this.route;
    while (r.firstChild) {
      r = r.firstChild;
    }
    return r.snapshot.data['subtitle'] ?? 'Premium Admin & Dashboard Template';
  });

  constructor() {
    setActiveStylesheet(
      '/assets/css/lazy/bootstrap-app.css',
      'lazy-bootstrap-app',
      BOOTSTRAP_LAYOUT_STYLE_GROUP,
    );
  }
}
