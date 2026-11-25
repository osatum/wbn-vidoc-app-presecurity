import { HttpContext, HttpContextToken, HttpErrorResponse, HttpRequest } from '@angular/common/http';

export const HANDLE_UNAUTHORIZED_LOCALLY = new HttpContextToken<boolean>(() => false);
export const SKIP_SESSION_REFRESH = new HttpContextToken<boolean>(() => false);

export function withLocalUnauthorizedHandling(): HttpContext {
  return new HttpContext().set(HANDLE_UNAUTHORIZED_LOCALLY, true);
}

export function isLocallyHandledUnauthorized(req: HttpRequest<unknown>, error: HttpErrorResponse): boolean {
  return req.context.get(HANDLE_UNAUTHORIZED_LOCALLY)
    && error.status === 401
    && isAuthenticationFailedError(error.error);
}

function isAuthenticationFailedError(error: unknown): boolean {
  if (typeof error !== 'object' || error === null) {
    return false;
  }

  return 'code' in error && error.code === 'AUTHENTICATION_FAILED';
}
