import { inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, Data, NavigationEnd, Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { of, Subscription } from 'rxjs';
import { filter, map, shareReplay, startWith, switchMap } from 'rxjs/operators';

@Injectable({
  providedIn: 'root',
})
export class PageMetaService {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly transloco = inject(TranslocoService);

  private subscription?: Subscription;

  initialize(): void {
    if (this.subscription) {
      return;
    }

    const routeData$ = this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      startWith(null),
      map(() => this.getDeepestRouteData()),
      shareReplay({ bufferSize: 1, refCount: true })
    );

    this.subscription = new Subscription();

    this.subscription.add(
      routeData$
        .pipe(
          map((data) => data['title']),
          switchMap((key) => this.translateRouteData(key))
        )
        .subscribe((value) => {
          if (value) {
            this.title.setTitle(value);
          }
        })
    );

    this.subscription.add(
      routeData$
        .pipe(
          map((data) => data['description']),
          switchMap((key) => this.translateRouteData(key))
        )
        .subscribe((value) => {
          if (value) {
            this.meta.updateTag({ name: 'description', content: value });
          }
        })
    );
  }

  private getDeepestRouteData(): Data {
    let currentRoute: ActivatedRoute | null = this.route;

    while (currentRoute?.firstChild) {
      currentRoute = currentRoute.firstChild;
    }

    return currentRoute?.snapshot.data ?? {};
  }

  private translateRouteData(value: unknown) {
    if (typeof value !== 'string' || value.trim().length === 0) {
      return of(null);
    }

    return this.transloco.selectTranslate(value);
  }
}
