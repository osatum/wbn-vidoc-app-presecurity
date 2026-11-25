import { Component } from '@angular/core';
import {RouterOutlet} from '@angular/router';
import { ToastsContainer } from '../../shared/toasts-container/toasts-container';
import { CookieConsent } from '../../shared/cookie-consent/cookie-consent';

@Component({
  selector: 'app-home-layout',
  imports: [RouterOutlet, ToastsContainer, CookieConsent],
  templateUrl: './home-layout.html',
  styleUrl: './home-layout.css',
})
export class HomeLayout {}
