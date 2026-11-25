import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import {map, Observable, of, switchMap, take} from 'rxjs';
import {AuthService} from '../services/auth.service';
import {UserProfileResponse} from '../backend';
import {RedirectService} from '../services/redirect-service';
import {catchError} from 'rxjs/operators';



export const systemOperatorGuard: CanActivateFn = (): Observable<boolean | UrlTree> => {
  const authService = inject(AuthService);
  const redirectService: RedirectService = inject(RedirectService);

  return authService.getUserProfile().pipe(
    take(1),

    switchMap(profile => {
      if (profile) {
        return of(profile);
      }

      return authService.silentRefresh().pipe(
        switchMap(isLoggedIn => {
          if (!isLoggedIn) {
            return of(null);
          }

          return authService.loadUserProfile().pipe(
            catchError(() => of(null))
          );
        }),
        catchError(() => of(null))
      );
    }),

    map(profile => {
      if (
        profile?.institutionType ===
        UserProfileResponse.InstitutionTypeEnum.SystemOperator
      ) {
        return true;
      }
      return redirectService.redirectToDenied(profile);
    })
  );
};
