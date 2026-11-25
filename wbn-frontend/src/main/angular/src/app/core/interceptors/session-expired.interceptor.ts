import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { isPublicApiRequest } from './public-api-paths';
import { isLocallyHandledUnauthorized, SKIP_SESSION_REFRESH } from './auth-error-context';

export const sessionExpiredInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const isPublicApi = isPublicApiRequest(req.url);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (isPublicApi || req.context.get(SKIP_SESSION_REFRESH)) {
        return throwError(() => error);
      }

      if (error.status === 401 && !isLocallyHandledUnauthorized(req, error)) {
        auth.clearSession();
        void router.navigate(['/']);
      }

      if (error.status === 403 && auth.token()) {
        return auth.refreshOnce().pipe(
          catchError(() => {
            auth.clearSession();
            void router.navigate(['/']);
            return throwError(() => error);
          }),
          switchMap(() => next(req)),
        );
      }

      return throwError(() => error);
    }),
  );
};
