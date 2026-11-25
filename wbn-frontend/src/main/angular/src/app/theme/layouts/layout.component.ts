import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  OnInit,
  inject,
} from '@angular/core';
import { Store } from '@ngrx/store';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { RootReducerState } from '../store';

import { VerticalComponent } from './vertical/vertical.component';
import { HorizontalComponent } from './horizontal/horizontal.component';
import { TwoColumnComponent } from './two-column/two-column.component';

@Component({
  selector: 'app-layout',
  templateUrl: './layout.component.html',
  styleUrls: ['./layout.component.scss'],
  imports: [VerticalComponent, HorizontalComponent, TwoColumnComponent],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Default,
})
export class LayoutComponent implements OnInit {
  // ustaw wartość startową (unikasz undefined w template)
  layoutType: string = 'vertical';

  private readonly store = inject<Store<RootReducerState>>(Store);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);

  ngOnInit(): void {
    this.store
      .select('layout')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data) => {
        this.layoutType = data.LAYOUT;

        const root = document.documentElement;

        root.setAttribute('data-layout', data.LAYOUT);
        root.setAttribute('data-bs-theme', data.LAYOUT_MODE);
        root.setAttribute('data-layout-width', data.LAYOUT_WIDTH);
        root.setAttribute('data-layout-position', data.LAYOUT_POSITION);
        root.setAttribute('data-topbar', data.TOPBAR);
        root.setAttribute('data-preloader', data.DATA_PRELOADER);
        root.setAttribute('data-sidebar-visibility', data.SIDEBAR_VISIBILITY);

        const hasSidebar = data.LAYOUT === 'vertical' || data.LAYOUT === 'twocolumn';

        if (hasSidebar) {
          root.setAttribute('data-sidebar', data.SIDEBAR_COLOR);
          root.setAttribute('data-sidebar-size', data.SIDEBAR_SIZE);
          root.setAttribute('data-sidebar-image', data.SIDEBAR_IMAGE);
          root.setAttribute('data-layout-style', data.SIDEBAR_VIEW);
        } else {
          // opcjonalnie: posprzątaj po przełączeniu layoutu bez sidebara
          root.removeAttribute('data-sidebar');
          root.removeAttribute('data-sidebar-size');
          root.removeAttribute('data-sidebar-image');
          root.removeAttribute('data-layout-style');
        }

        // kluczowe dla NG0100 w układach warunkowych w template
        this.cdr.detectChanges();
      });
  }

  isVerticalLayoutRequested(): boolean {
    return this.layoutType === 'vertical';
  }

  isSemiboxLayoutRequested(): boolean {
    return this.layoutType === 'semibox';
  }

  isHorizontalLayoutRequested(): boolean {
    return this.layoutType === 'horizontal';
  }

  isTwoColumnLayoutRequested(): boolean {
    return this.layoutType === 'twocolumn';
  }
}
