import {
  HttpContext,
  HttpContextToken,
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpResponse,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, tap, throwError } from 'rxjs';
import {
  DISABLE_NAVIGATION_AUDIT,
  NavigationAuditService,
  NavigationAuditViewContext,
} from '../services/navigation-audit.service';

export type NavigationAuditOperation = {
  name: string;
  viewContext?: NavigationAuditViewContext;
};

export const NAVIGATION_AUDIT_OPERATION =
  new HttpContextToken<NavigationAuditOperation | null>(() => null);

export function withNavigationAuditOperation(operation: NavigationAuditOperation): HttpContext {
  return new HttpContext().set(NAVIGATION_AUDIT_OPERATION, operation);
}

export const navigationAuditInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.context.get(DISABLE_NAVIGATION_AUDIT)) {
    return next(req);
  }

  const operation = req.context.get(NAVIGATION_AUDIT_OPERATION);
  if (!operation) {
    return next(req);
  }

  const navigationAudit = inject(NavigationAuditService);
  const viewContext = operation.viewContext ?? navigationAudit.captureView();
  const startedAt = performance.now();

  return next(req).pipe(
    tap((event) => {
      if (!(event instanceof HttpResponse)) {
        return;
      }

      navigationAudit.track({
        ...viewContext,
        eventType: 'API_RESULT',
        action: operation.name,
        durationMs: Math.round(performance.now() - startedAt),
        success: true,
        statusCode: event.status,
        properties: {
          method: req.method,
        },
      });
    }),
    catchError((error: unknown) => {
      navigationAudit.track({
        ...viewContext,
        eventType: 'API_RESULT',
        action: operation.name,
        durationMs: Math.round(performance.now() - startedAt),
        success: false,
        statusCode:
          error instanceof HttpErrorResponse && error.status >= 100 && error.status <= 599
            ? error.status
            : undefined,
        properties: {
          method: req.method,
        },
      });

      return throwError(() => error);
    }),
  );
};
