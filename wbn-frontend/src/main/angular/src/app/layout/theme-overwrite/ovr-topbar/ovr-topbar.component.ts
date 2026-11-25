import { AsyncPipe, NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgbDropdown, NgbDropdownModule, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { SimplebarAngularModule } from 'simplebar-angular';

import { TopbarComponent } from '../../../theme/layouts/topbar/topbar.component';
import { THEME_EVENTS } from '../../../theme/core/services/event.service';
import { LanguageService } from '../../../core/services/language-service';
import { AuthService } from '../../../core/services/auth.service';
import { ProfileAccess, UserProfileResponse } from '../../../core/backend';
import { RedirectService } from '../../../core/services/redirect-service';
import { switchMap, take } from 'rxjs';
import { Router } from '@angular/router';

@Component({
  selector: 'app-ovr-topbar',
  templateUrl: './ovr-topbar.component.html',
  styleUrls: ['./ovr-topbar.component.scss'],
  imports: [
    FormsModule,
    SimplebarAngularModule,
    NgClass,
    NgbDropdown,
    NgbDropdownModule,
    NgbModule,
    AsyncPipe,
  ],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Default,
})
export class OvrTopbarComponent extends TopbarComponent {
  private readonly langService: LanguageService = inject(LanguageService);
  private readonly authenticationService: AuthService = inject(AuthService);
  private readonly redirectService: RedirectService = inject(RedirectService);
  private readonly appRouter = inject(Router);
  protected readonly langs = this.langService.getLanguageItems();
  protected activeLang = this.langService.getActiveLang();
  userProfile$ = this.authenticationService.userProfile$;

  override ngOnInit(): any {
    super.ngOnInit();
    this.langService.langChanges().subscribe(() => {
      this.activeLang = this.langService.getActiveLang();
    });

    console.log('Topbar initialized with active language:', this.activeLang);
  }

  /**
   * Toggle the menu bar when having mobile screen
   */
  override toggleMobileMenu(event: Event): void {
    event.preventDefault();
    this.document.querySelector('.hamburger-icon')?.classList.toggle('open');
    this.mobileMenuButtonClicked.emit();
    this.eventService.broadcast(THEME_EVENTS.mobileMenuButtonClicked);
  }

  /***
   * Language Listing
   */

  getLanguages() {
    return this.langs;
  }

  override initLanguageFromCookie(): void {}

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
      this.appRouter.navigate(['/system/admin/change-password']);
      return;
    }

    this.appRouter.navigate(['/institution/user/change-password']);
  }

  protected canDeleteOwnData(profile: UserProfileResponse): boolean {
    return profile.institutionType === UserProfileResponse.InstitutionTypeEnum.ResearchUnit;
  }

  protected deleteOwnData(): void {
    this.appRouter.navigate(['/institution/user/delete-my-data']);
  }
}
