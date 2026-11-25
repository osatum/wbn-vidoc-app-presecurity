import { Component } from '@angular/core';
import {RouterLink, RouterOutlet} from "@angular/router";
import {
  BOOTSTRAP_LAYOUT_STYLE_GROUP,
  setActiveStylesheet,
} from '../../core/feature/styles-loader';
import {DefaultHeader} from '../../shared/default-header/default-header';
import {ToastsContainer} from '../../shared/toasts-container/toasts-container';
import {DefaultFooter} from '../../shared/default-footer/default-footer';

@Component({
  selector: 'app-system-layout',
  imports: [
    RouterOutlet,
    DefaultHeader,
    ToastsContainer,
    DefaultFooter,
    RouterLink
  ],
  templateUrl: './system-layout.html',
  styleUrl: './system-layout.css',
})
export class SystemLayout {
  constructor() {
    setActiveStylesheet(
      '/assets/css/lazy/bootstrap-app.css',
      'lazy-bootstrap-app',
      BOOTSTRAP_LAYOUT_STYLE_GROUP
    );
  }
}
