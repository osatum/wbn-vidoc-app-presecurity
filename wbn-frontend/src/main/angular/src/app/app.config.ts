import {APP_INITIALIZER, ApplicationConfig, inject, provideBrowserGlobalErrorListeners} from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import {provideTransloco} from '@jsverse/transloco';
import {TranslocoHttpLoader} from './core/services/transloco-http-loader';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {authInterceptor} from './core/interceptors/auth.interceptor';
import {loadUserInterceptor} from './core/interceptors/load-user-interceptor';
import {sessionExpiredInterceptor} from './core/interceptors/session-expired.interceptor';
import {sessionActivityInterceptor} from './core/interceptors/session-activity.interceptor';
import {navigationAuditInterceptor} from './core/interceptors/navigation-audit.interceptor';
import {BASE_PATH, Configuration} from './core/backend';
import {authInitializer} from './core/auth.initializer';
import { provideStore } from '@ngrx/store';
import { rootReducer } from './theme/store';
import { PageMetaService } from './core/services/page-meta-service';
import { RouteNavigationAuditService } from './core/services/route-navigation-audit.service';
import { NavigationVisitActivityService } from './core/services/navigation-visit-activity.service';
import { environment } from '../environments/environment';

export function pageMetaInitializer() {
  const pageMetaService = inject(PageMetaService);

  return () => pageMetaService.initialize();
}

export function navigationAuditInitializer() {
  const routeNavigationAuditService = inject(RouteNavigationAuditService);
  const visitActivity = inject(NavigationVisitActivityService);

  return () => {
    routeNavigationAuditService.initialize();
    visitActivity.initialize();
  };
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideStore(rootReducer),
    provideHttpClient(withInterceptors([sessionExpiredInterceptor, authInterceptor, navigationAuditInterceptor, sessionActivityInterceptor, loadUserInterceptor])),

    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: authInitializer,
    },
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: pageMetaInitializer,
    },
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: navigationAuditInitializer,
    },
    // basePath pusty => idzie na ten sam origin (4200) i proxy działa
    { provide: BASE_PATH, useValue: environment.apiBasePath },

    // cookie ma być wysyłane
    {
      provide: Configuration,
      useValue: new Configuration({ basePath: environment.apiBasePath, withCredentials: true }),
    },

    provideTransloco({
      config: {
        availableLangs: ['en', 'pl'],
        defaultLang: 'pl',
        // Remove this option if your application doesn't support changing language in runtime.
        reRenderOnLangChange: true,
        //prodMode: !isDevMode(),
        prodMode: true,
      },
      loader: TranslocoHttpLoader,
    }),
  ],
};
