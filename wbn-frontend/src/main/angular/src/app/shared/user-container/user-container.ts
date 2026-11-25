import {Component, inject, OnInit} from '@angular/core';
import {AsyncPipe} from "@angular/common";
import {
    NgbDropdown,
    NgbDropdownButtonItem,
    NgbDropdownItem,
    NgbDropdownMenu,
    NgbDropdownToggle
} from "@ng-bootstrap/ng-bootstrap";
import {Observable} from 'rxjs';

import {AuthService} from '../../core/services/auth.service';
import {ThemeService} from '../../core/services/theme-service';
import {Router} from '@angular/router';
import { UserProfileResponse } from '../../core/backend';

@Component({
  selector: 'app-user-container',
    imports: [
        AsyncPipe,
        NgbDropdown,
        NgbDropdownButtonItem,
        NgbDropdownItem,
        NgbDropdownMenu,
        NgbDropdownToggle
    ],
  templateUrl: './user-container.html',
  styleUrl: './user-container.css',
})
export class UserContainer  implements OnInit {
  userProfile$: Observable<UserProfileResponse | null> | null = null;
  authService: AuthService = inject(AuthService);
  router: Router = inject(Router)

  ngOnInit() {
    this.userProfile$ = this.authService.userProfile$;
  }

  protected login() {
    this.authService.resetPendingInstitution();
    this.router.navigate(['/auth/login']);
  }
  protected logout() {
    this.authService.logout();
  }
}
