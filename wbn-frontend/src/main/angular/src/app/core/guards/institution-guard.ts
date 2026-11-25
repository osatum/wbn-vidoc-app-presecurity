import {CanActivateFn, Router} from '@angular/router';
import {inject} from '@angular/core';
import {AuthService} from '../services/auth.service';
import {map, of, switchMap, take} from 'rxjs';
import {UserProfileResponse} from '../backend';
import {RedirectService} from '../services/redirect-service';
import {catchError} from 'rxjs/operators';

export const institutionGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const redirectService: RedirectService = inject(RedirectService);

  console.log('institutionGuard');

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
        UserProfileResponse.InstitutionTypeEnum.ResearchUnit
      ) {
        return true;
      }

      return redirectService.redirectToDenied(profile);
    })
  );
};
