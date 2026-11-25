import {inject} from '@angular/core';
import {HttpErrorResponse, HttpInterceptorFn} from '@angular/common/http';
import {catchError, switchMap, throwError} from 'rxjs';
import {AuthService} from '../services/auth.service';
import {isPublicApiRequest} from './public-api-paths';
import {isLocallyHandledUnauthorized, SKIP_SESSION_REFRESH} from './auth-error-context';

export const authInterceptor: HttpInterceptorFn = (req, next) => {

  const auth = inject(AuthService);

  const token = auth.token();
  const isPublicApi = isPublicApiRequest(req.url);
  const isContextSelectionRequest = req.url.includes('/api/auth/select-institution');

  const authReq = ((!isPublicApi || isContextSelectionRequest) && token)
    ? req.clone({setHeaders: {Authorization: `Bearer ${token}`}})
    : req;

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (!isPublicApi && isIpAccessDenied(err)) {
        auth.handleIpAccessDenied();
        return throwError(() => err);
      }

      if (err.status !== 401 || isPublicApi || isLocallyHandledUnauthorized(req, err) || req.context.get(SKIP_SESSION_REFRESH)) {
        return throwError(() => err);
      }


      return auth.refreshOnce().pipe(
        switchMap(() => {
          const newToken = auth.token();
          if (!newToken) {
            return throwError(() => err);
          }
          return next(
            req.clone({
              setHeaders: {Authorization: `Bearer ${newToken}`}
            })
          );
        })
      );
    })
  );
};

function isIpAccessDenied(error: HttpErrorResponse): boolean {
  return error.status === 403 &&
    typeof error.error === 'object' &&
    error.error !== null &&
    'code' in error.error &&
    error.error.code === 'IP_ACCESS_DENIED';
}
