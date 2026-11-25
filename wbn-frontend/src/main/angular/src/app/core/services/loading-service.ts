import { Injectable, computed, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class LoadingService {
  private readonly appReady = signal(false);
  private readonly routerLoading = signal(false);
  private readonly visible = signal(false);

  private readonly SHOW_DELAY = 150;
  private readonly MIN_VISIBLE = 300;

  private showTimeout: any = null;
  private hideTimeout: any = null;
  private visibleSince = 0;

  readonly loading = computed(() => this.appReady() && this.visible());

  setAppReady() {
    if (this.appReady()) return;
    this.appReady.set(true);
    this.sync();
  }

  setRouterLoading(v: boolean) {
    this.routerLoading.set(v);
    this.sync();
  }

  private sync() {
    if (!this.appReady()) {
      this.clearShowTimeout();
      this.clearHideTimeout();
      this.visible.set(false);
      return;
    }

    const shouldBeLoading = this.routerLoading();

    if (shouldBeLoading) this.scheduleShow();
    else this.scheduleHide();
  }

  private scheduleShow() {
    if (this.visible()) return;

    this.clearHideTimeout();
    this.clearShowTimeout();

    this.showTimeout = setTimeout(() => {
      this.visibleSince = Date.now();
      this.visible.set(true);
      this.showTimeout = null;
    }, this.SHOW_DELAY);
  }

  private scheduleHide() {
    this.clearShowTimeout();
    if (!this.visible()) return;

    this.clearHideTimeout();

    const elapsed = Date.now() - this.visibleSince;
    const remaining = Math.max(0, this.MIN_VISIBLE - elapsed);

    this.hideTimeout = setTimeout(() => {
      this.visible.set(false);
      this.hideTimeout = null;
    }, remaining);
  }

  private clearShowTimeout() {
    if (this.showTimeout !== null) {
      clearTimeout(this.showTimeout);
      this.showTimeout = null;
    }
  }

  private clearHideTimeout() {
    if (this.hideTimeout !== null) {
      clearTimeout(this.hideTimeout);
      this.hideTimeout = null;
    }
  }
}
