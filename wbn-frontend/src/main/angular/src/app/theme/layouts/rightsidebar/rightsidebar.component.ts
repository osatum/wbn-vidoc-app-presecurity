import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  EventEmitter,
  OnInit,
  Output,
  TemplateRef,
  ViewChild,
  inject,
} from '@angular/core';
import { NgbOffcanvas } from '@ng-bootstrap/ng-bootstrap';
import { Store } from '@ngrx/store';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SimplebarAngularModule } from 'simplebar-angular';

import { RootReducerState } from '../../store';

import {
  changeDataPreloader,
  changelayout,
  changeLayoutPosition,
  changeLayoutWidth,
  changeMode,
  changeSidebarColor,
  changeSidebarImage,
  changeSidebarSize,
  changeSidebarView,
  changeSidebarVisibility,
  changeTopbar,
} from '../../store/layouts/layout-action';
import { EventService } from '../../core/services/event.service';


@Component({
  selector: 'app-rightsidebar',
  templateUrl: './rightsidebar.component.html',
  styleUrls: ['./rightsidebar.component.scss'],
  imports: [SimplebarAngularModule],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Default,
})
export class RightsidebarComponent implements OnInit, AfterViewInit {
  private readonly eventService = inject(EventService);
  private readonly offcanvasService = inject(NgbOffcanvas);
  private readonly store = inject<Store<RootReducerState>>(Store);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);

  layout?: string;
  mode?: string;
  width?: string;
  position?: string;
  topbar?: string;
  size?: string;
  sidebarView?: string;
  sidebar?: string;
  sidebarImage?: string;
  sidebarVisibility?: string;
  preLoader?: string;

  attribute: any;
  grd: any;

  @ViewChild('filtetcontent') filtetcontent!: TemplateRef<any>;
  @Output() settingsButtonClicked = new EventEmitter<void>();

  ngOnInit(): void {
    // Jedna subskrypcja na layout: ustawia pola + atrybuty HTML
    this.store
      .select('layout')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data) => {
        this.layout = data.LAYOUT;
        this.mode = data.LAYOUT_MODE;
        this.width = data.LAYOUT_WIDTH;
        this.position = data.LAYOUT_POSITION;
        this.topbar = data.TOPBAR;
        this.size = data.SIDEBAR_SIZE;
        this.sidebarView = data.SIDEBAR_VIEW;
        this.sidebar = data.SIDEBAR_COLOR;
        this.sidebarImage = data.SIDEBAR_IMAGE;
        this.preLoader = data.DATA_PRELOADER;
        this.sidebarVisibility = data.SIDEBAR_VISIBILITY;

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
          root.removeAttribute('data-sidebar');
          root.removeAttribute('data-sidebar-size');
          root.removeAttribute('data-sidebar-image');
          root.removeAttribute('data-layout-style');
        }

        // dopięcie zmian do CD (anty-NG0100)
        this.cdr.detectChanges();
      });
  }

  ngAfterViewInit(): void {
    // Otwórz offcanvas po renderze (zamiast setTimeout 1000)
    queueMicrotask(() => {
      if (!this.offcanvasService.hasOpenOffcanvas()) {
        //this.openEnd(this.filtetcontent);
      }
    });
  }

  /**
   * Change the layout onclick
   */
  changeLayout(layout: string) {
    this.attribute = layout;
    this.store.dispatch(changelayout({ layout }));

    queueMicrotask(() => window.dispatchEvent(new Event('resize')));
  }

  // Add Active Class
  addActive(grdSidebar: any) {
    this.grd = grdSidebar;
    document.documentElement.setAttribute('data-sidebar', grdSidebar);
    document.getElementById('collapseBgGradient')?.classList.toggle('show');
    document.getElementById('collapseBgGradient1')?.classList.add('active');
    this.cdr.detectChanges();
  }

  // Remove Active Class
  removeActive() {
    this.grd = '';
    document.getElementById('collapseBgGradient1')?.classList.remove('active');
    document.getElementById('collapseBgGradient')?.classList.remove('show');
    this.cdr.detectChanges();
  }

  topFunction() {
    document.body.scrollTop = 0;
    document.documentElement.scrollTop = 0;
  }

  // Filter Offcanvas Set
  openEnd(content: TemplateRef<any>) {
    this.offcanvasService.open(content, { position: 'end' });

    // zamiast setAttribute('checked','true') → ustaw checked property
    queueMicrotask(() => {
      this.attribute = document.documentElement.getAttribute('data-layout');

      const map: Record<string, string> = {
        vertical: 'customizer-layout01',
        horizontal: 'customizer-layout02',
        twocolumn: 'customizer-layout03',
        semibox: 'customizer-layout04',
      };

      const id = map[this.attribute];
      const el = id ? (document.getElementById(id) as HTMLInputElement | null) : null;
      if (el) el.checked = true;

      this.cdr.detectChanges();
    });
  }

  // Mode Change
  changeLayoutMode(mode: string) {
    this.mode = mode;
    this.store.dispatch(changeMode({ mode }));
  }

  // Visibility Change
  changeVisibility(sidebarvisibility: string) {
    this.sidebarVisibility = sidebarvisibility;
    this.store.dispatch(changeSidebarVisibility({ sidebarvisibility }));
  }

  // Width Change
  changeWidth(layoutWidth: string, size: string) {
    this.width = layoutWidth;
    this.store.dispatch(changeLayoutWidth({ layoutWidth }));

    // jeśli sidebar size ma się też zmieniać → akcja do store
    this.store.dispatch(changeSidebarSize({ sidebarSize: size }));

    queueMicrotask(() => window.dispatchEvent(new Event('resize')));
  }

  // Position Change
  changePosition(layoutPosition: string) {
    this.position = layoutPosition;
    this.store.dispatch(changeLayoutPosition({ layoutPosition }));
  }

  // Topbar Change
  changeTopColor(topbarColor: string) {
    this.topbar = topbarColor;
    this.store.dispatch(changeTopbar({ topbarColor }));
  }

  // Sidebar Size Change
  changeSidebarSize(sidebarSize: string) {
    this.size = sidebarSize;
    this.store.dispatch(changeSidebarSize({ sidebarSize }));
  }

  // Sidebar View Change
  changeSidebar(sidebarView: string) {
    this.sidebarView = sidebarView;
    this.store.dispatch(changeSidebarView({ sidebarView }));
  }

  // Sidebar Color Change
  changeSidebarColor(sidebarColor: string) {
    this.sidebar = sidebarColor;
    this.store.dispatch(changeSidebarColor({ sidebarColor }));
  }

  // Sidebar Image Change
  changeSidebarImage(sidebarImage: string) {
    this.sidebarImage = sidebarImage;
    this.store.dispatch(changeSidebarImage({ sidebarImage }));
  }

  // PreLoader Change
  changeLoader(Preloader: string) {
    this.preLoader = Preloader;
    this.store.dispatch(changeDataPreloader({ Preloader }));

    const preloader = document.getElementById('preloader');
    if (preloader) {
      setTimeout(() => {
        const p = document.getElementById('preloader') as HTMLElement | null;
        if (!p) return;
        p.style.opacity = '0';
        p.style.visibility = 'hidden';
      }, 1000);
    }
  }
}
