import { Component, inject, Input } from '@angular/core';
import { AsyncPipe, DOCUMENT, NgClass } from '@angular/common';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { LanguageService } from '../../core/services/language-service';
import { AuthService } from '../../core/services/auth.service';
import { Router } from '@angular/router';
import { RedirectService } from '../../core/services/redirect-service';
import { EventService } from '../../theme/core/services/event.service';
import { ProfileAccess, UserProfileResponse } from '../../core/backend';
import { switchMap, take } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';

type ThemeMode = 'light' | 'dark';
@Component({
  selector: 'app-default-header',
  imports: [AsyncPipe, NgbDropdown, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip, NgClass, TranslocoPipe],
  templateUrl: './default-header.html',
  styleUrl: './default-header.css',
})
export class DefaultHeader {
  protected readonly langService: LanguageService = inject(LanguageService);
  protected readonly authenticationService: AuthService = inject(AuthService);
  protected readonly router = inject(Router);
  protected readonly redirectService: RedirectService = inject(RedirectService);
  protected readonly eventService = inject(EventService);
  protected activeLang = this.langService.getActiveLang();
  protected readonly langs = this.langService.getLanguageItems();

  @Input() routerLink?: string;

  userProfile$ = this.authenticationService.userProfile$;
  element!: HTMLElement;
  mode: ThemeMode = 'light';
  protected readonly document = inject<Document>(DOCUMENT);

  isHomeLink() {
    if (this.routerLink === undefined) {
      return false;
    }
    return true;
  }

  goToHome() {
    if (this.isHomeLink()) this.router.navigate([this.routerLink]);
  }

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

  protected login() {
    this.authenticationService.resetPendingInstitution();
    this.router.navigate(['/auth/login']);
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
    this.activeLang = this.langService.getActiveLang();
  }

  protected selectContext(access: ProfileAccess) {
    if (access.institutionId && access.membershipId) {
      this.authenticationService
        .selectInstitution(access.institutionId, access.membershipId)
        .pipe(switchMap(() => this.authenticationService.getUserProfile().pipe(take(1))))
        .subscribe((profile) => {
          this.redirectService.redirectToDashboard(profile);
        });
    }
  }

  protected changePassword(profile: UserProfileResponse) {
    if (profile.institutionType === UserProfileResponse.InstitutionTypeEnum.SystemOperator) {
      this.router.navigate(['/system/admin/change-password']);
      return;
    }

    this.router.navigate(['/institution/user/change-password']);
  }

  protected canDeleteOwnData(profile: UserProfileResponse): boolean {
    return (
      profile.institutionType === UserProfileResponse.InstitutionTypeEnum.ResearchUnit &&
      profile.userId !== undefined &&
      profile.userId !== null
    );
  }

  protected deleteOwnData(): void {
    this.router.navigate(['/institution/user/delete-my-data']);
  }

  /**
   * Fullscreen method
   */
  fullscreen(): void {
    this.document.body.classList.toggle('fullscreen-enable');

    console.log('Fullscreen toggled');

    const doc: any = this.document as any;
    const el: any = this.document.documentElement as any;

    const isFull =
      doc.fullscreenElement ||
      doc.mozFullScreenElement ||
      doc.webkitFullscreenElement ||
      doc.msFullscreenElement;

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

  windowScroll(): void {
    const backToTop = this.document.getElementById('back-to-top') as HTMLElement | null;
    const topbar = this.document.getElementById('page-topbar');

    const scrolled =
      this.document.body.scrollTop > 100 || this.document.documentElement.scrollTop > 100;

    if (backToTop) backToTop.style.display = scrolled ? 'block' : 'none';
    topbar?.classList.toggle('topbar-shadow', scrolled);
  }

  protected register() {
    this.authenticationService.resetPendingInstitution();
    this.router.navigate(['/registration']);
  }

  protected canChangeOwnBasicData(profile: UserProfileResponse): boolean {
    return (
      profile.institutionType === UserProfileResponse.InstitutionTypeEnum.ResearchUnit &&
      profile.userId !== undefined &&
      profile.userId !== null
    );
  }

  protected changeOwnBasicData(): void {
    this.router.navigate(['/institution/user/profile/basic-data']);
  }
  protected canChangePassword(profile: UserProfileResponse): boolean {
    return (
      profile.userId !== undefined &&
      profile.userId !== null
    );
  }
}
