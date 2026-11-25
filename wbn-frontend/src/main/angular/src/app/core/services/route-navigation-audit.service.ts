import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { NavigationAuditResource, NavigationAuditService } from './navigation-audit.service';

type RouteNavigationAuditConfig = {
  enabled: boolean;
  pageKey: string;
  routeTemplate: string;
  publicView?: boolean;
  params?: string[];
  resource?: {
    type: string;
    idParam: string;
  };
};

@Injectable({
  providedIn: 'root',
})
export class RouteNavigationAuditService {
  private readonly router = inject(Router);
  private readonly navigationAudit = inject(NavigationAuditService);
  private initialized = false;

  initialize(): void {
    if (this.initialized) {
      return;
    }

    this.initialized = true;
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        const snapshot = this.deepestChild(this.router.routerState.snapshot.root);
        const config = snapshot.data['analytics'] as RouteNavigationAuditConfig | undefined;

        if (!config?.enabled) {
          return;
        }

        const params = this.extractAllowedParams(snapshot, config.params ?? []);
        const resource = this.extractResource(config, params);

        this.navigationAudit.startView({
          pageKey: config.pageKey,
          routeTemplate: config.routeTemplate,
          url: event.urlAfterRedirects,
          params,
          resource,
          publicView: config.publicView,
        });
      });
  }

  private deepestChild(snapshot: ActivatedRouteSnapshot): ActivatedRouteSnapshot {
    let current = snapshot;

    while (current.firstChild) {
      current = current.firstChild;
    }

    return current;
  }

  private extractAllowedParams(
    snapshot: ActivatedRouteSnapshot,
    names: string[],
  ): Record<string, string> {
    const result: Record<string, string> = {};

    for (const name of names) {
      const value = snapshot.paramMap.get(name);
      if (value !== null) {
        result[name] = value;
      }
    }

    return result;
  }

  private extractResource(
    config: RouteNavigationAuditConfig,
    params: Record<string, string>,
  ): NavigationAuditResource | undefined {
    if (!config.resource) {
      return undefined;
    }

    return {
      type: config.resource.type,
      id: params[config.resource.idParam],
    };
  }
}
