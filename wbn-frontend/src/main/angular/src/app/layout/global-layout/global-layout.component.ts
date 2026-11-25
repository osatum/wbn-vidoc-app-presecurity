import { Component } from '@angular/core';
import { CookieConsent } from '../../shared/cookie-consent/cookie-consent';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-global-layout',
  imports: [CookieConsent, RouterOutlet],
  templateUrl: './global-layout.component.html',
  styleUrl: './global-layout.component.css',
})
export class GlobalLayoutComponent {}
