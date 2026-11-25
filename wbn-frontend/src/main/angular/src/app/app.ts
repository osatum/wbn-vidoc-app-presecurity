import { Component, DestroyRef, inject, afterNextRender } from '@angular/core';
import {
  NavigationCancel,
  NavigationEnd,
  NavigationError,
  NavigationStart,
  Router,
  RouterOutlet
} from '@angular/router';
import { filter, take } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LoadingService } from './core/services/loading-service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {

  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  protected loadingService = inject(LoadingService);

  constructor() {
    // --- OPCJA 1: splash z index.html ---
    let splashRemoved = false;

    const removeSplash = () => {
      if (splashRemoved) return;
      splashRemoved = true;

      document.querySelector('.app-splash')?.remove();
      this.loadingService.setAppReady();
    };

    // usuń splash po pierwszym renderze
    afterNextRender(() => removeSplash());

    // fallback: po pierwszym NavigationEnd (gdyby render był opóźniony)
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        take(1),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => removeSplash());

    // --- OPCJA 2: router loader (tylko po appReady) ---
    this.router.events
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(e => {
        // dopóki splash nie zdjęty – nie włączamy global loadera
        if (!splashRemoved) return;

        if (e instanceof NavigationStart) this.loadingService.setRouterLoading(true);

        if (e instanceof NavigationEnd || e instanceof NavigationCancel || e instanceof NavigationError) {
          this.loadingService.setRouterLoading(false);
        }
      });
  }

}
