import { Component, inject, OnInit } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { BOOTSTRAP_LAYOUT_STYLE_GROUP, setActiveStylesheet } from '../../core/feature/styles-loader';
import { DefaultHeader } from '../../shared/default-header/default-header';
import { ToastsContainer } from '../../shared/toasts-container/toasts-container';
import { PortalFooter } from '../../shared/portal-footer/portal-footer';
import { AsyncPipe, DOCUMENT } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { LanguageService } from '../../core/services/language-service';
import { AuthService } from '../../core/services/auth.service';
import { RedirectService } from '../../core/services/redirect-service';
import { EventService } from '../../theme/core/services/event.service';
import { ProfileAccess } from '../../core/backend';
import { switchMap, take } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';
import { OvrHorizontalComponent } from '../theme-overwrite/ovr-horizontal/ovr-horizontal.component';
import { OvrFooterComponent } from '../theme-overwrite/ovr-footer/ovr-footer.component';
import { OvrHorizontalTopbarComponent } from '../theme-overwrite/ovr-horizontal-topbar/ovr-horizontal-topbar.component';
import { OvrTopbarComponent } from '../theme-overwrite/ovr-topbar/ovr-topbar.component';
import { PortalHeader } from '../../shared/portal-header/portal-header';

type ThemeMode = 'light' | 'dark';
@Component({
  selector: 'app-portal-layout',
  imports: [
    RouterOutlet,
    DefaultHeader,
    ToastsContainer,
    PortalFooter,
    AsyncPipe,
    FormsModule,
    NgbDropdown,
    NgbDropdownMenu,
    NgbDropdownToggle,
    ReactiveFormsModule,
    RouterLink,
    TranslocoPipe,
    OvrHorizontalComponent,
    OvrFooterComponent,
    OvrHorizontalTopbarComponent,
    OvrTopbarComponent,
    PortalHeader,
  ],
  templateUrl: './portal-layout.html',
  styleUrl: './portal-layout.css',
})
export class PortalLayout implements OnInit {
  constructor() {
    setActiveStylesheet(
      '/assets/css/lazy/bootstrap-portal.css',
      'lazy-bootstrap-portal',
      BOOTSTRAP_LAYOUT_STYLE_GROUP,
    );
  }

  private readonly langService: LanguageService = inject(LanguageService);
  private readonly authenticationService: AuthService = inject(AuthService);
  private readonly redirectService: RedirectService = inject(RedirectService);
  protected readonly langs = this.langService.getLanguageItems();
  protected activeLang = this.langService.getActiveLang();
  userProfile$ = this.authenticationService.userProfile$;
  protected readonly eventService = inject(EventService);
  private readonly router = inject(Router);

  element!: HTMLElement;
  mode: ThemeMode = 'light';
  protected readonly document = inject<Document>(DOCUMENT);

  ngOnInit(): any {
    this.langService.langChanges().subscribe(() => {
      this.activeLang = this.langService.getActiveLang();
    });

    console.log('Topbar initialized with active language:', this.activeLang);
  }
  /**
   * Fullscreen method
   */
  fullscreen(): void {
    this.document.body.classList.toggle('fullscreen-enable');

    const doc: any = this.document as any;
    const el: any = this.element as any;

    const isFull =
      this.document.fullscreenElement || el.mozFullScreenElement || el.webkitFullscreenElement;

    if (!isFull) {
      if (el.requestFullscreen) el.requestFullscreen();
      else if (el.mozRequestFullScreen) el.mozRequestFullScreen();
      else if (el.webkitRequestFullscreen) el.webkitRequestFullscreen();
      else if (el.msRequestFullscreen) el.msRequestFullscreen();
    } else {
      if (doc.exitFullscreen) doc.exitFullscreen();
      else if (doc.mozCancelFullScreen) doc.mozCancelFullScreen();
      else if (doc.webkitExitFullscreen) doc.webkitExitFullscreen();
      else if (doc.msExitFullscreen) doc.msExitFullscreen();
    }
  }
  /**
   * Logout the user
   */
  logout(): void {
    this.authenticationService.logout();
    this.router.navigate(['/']);
  }

  /***
   * Language Value Set
   */
  setLanguage(text: string, lang: string, flag: string): void {
    this.langService.setLang(lang);
  }

  /**
   * Toggle the menu bar when having mobile screen
   */
  toggleMobileMenu(event: Event): void {}

  /***
   * Language Listing
   */

  getLanguages() {
    return this.langs;
  }

  /**
   * Topbar Light-Dark Mode Change
   */
  changeMode(mode: ThemeMode): void {
    this.mode = mode;
    this.eventService.broadcast('changeMode', mode);
    this.document.documentElement.setAttribute('data-bs-theme', mode);
  }

  windowScroll(): void {
    const backToTop = this.document.getElementById('back-to-top') as HTMLElement | null;
    const topbar = this.document.getElementById('page-topbar');

    const scrolled =
      this.document.body.scrollTop > 100 || this.document.documentElement.scrollTop > 100;

    if (backToTop) backToTop.style.display = scrolled ? 'block' : 'none';
    topbar?.classList.toggle('topbar-shadow', scrolled);
  }

  protected selctContext(access: ProfileAccess) {
    if (access.institutionId && access.membershipId) {
      this.authenticationService
        .selectInstitution(access.institutionId, access.membershipId)
        .pipe(switchMap(() => this.authenticationService.getUserProfile().pipe(take(1))))
        .subscribe((profile) => {
          this.redirectService.redirectToDashboard(profile);
        });
    }
  }
}
