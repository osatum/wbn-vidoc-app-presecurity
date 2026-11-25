import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  ElementRef,
  EventEmitter,
  OnInit,
  Output,
  ViewChild,
  inject,
} from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { NgClass } from '@angular/common';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { SimplebarAngularModule } from 'simplebar-angular';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { MENU } from './menu';
import { MenuItem } from './menu.model';


import { filter, startWith } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { environment } from '../../../../environments/environment';
import { SessionExpiration } from '../../../shared/session-expiration/session-expiration';

@Component({
  selector: 'app-sidebar',
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
  imports: [TranslocoPipe, RouterLink, NgClass, NgbCollapse, SimplebarAngularModule, SessionExpiration],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Default,
})
export class SidebarComponent implements OnInit, AfterViewInit {
  menu: any;
  toggle: any = true;
  menuItems: MenuItem[] = [];

  @ViewChild('sideMenu') sideMenu!: ElementRef;
  @Output() mobileMenuButtonClicked = new EventEmitter<void>();

  private readonly router = inject(Router);
  private readonly translate = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);

  ngOnInit(): void {
    this.translate.setDefaultLang('en');

    // Menu Items
    this.menuItems = MENU;

    // Jedna subskrypcja: start + każda nawigacja
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        startWith(null), // uruchom raz na starcie
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => {
        if (document.documentElement.getAttribute('data-layout') !== 'twocolumn') {
          this.safeInitActiveMenu();
        }
      });
  }

  ngAfterViewInit(): void {
    // Jeśli DOM menu renderuje się chwilę później (np. translacje),
    // wykonaj jeszcze jedno „bezpieczne” dopięcie po inicjalizacji widoku:
    queueMicrotask(() => this.safeInitActiveMenu());
  }

  /** init + dopięcie do detekcji zmian (anty-NG0100) */
  private safeInitActiveMenu(): void {
    this.initActiveMenu();
    this.cdr.detectChanges();
  }

  removeActivation(items: any) {
    items.forEach((item: any) => item.classList.remove('active'));
  }

  toggleItem(item: any) {
    if (!item) return;

    this.menuItems.forEach((menuItem: any) => {
      if (menuItem === item) {
        menuItem.isCollapsed = !menuItem.isCollapsed;
      } else {
        menuItem.isCollapsed = true;
      }

      if (menuItem.subItems) {
        menuItem.subItems.forEach((subItem: any) => {
          if (subItem === item) {
            menuItem.isCollapsed = !menuItem.isCollapsed;
            subItem.isCollapsed = !subItem.isCollapsed;
          } else {
            subItem.isCollapsed = true;
          }

          if (subItem.subItems) {
            subItem.subItems.forEach((childitem: any) => {
              if (childitem === item) {
                childitem.isCollapsed = !childitem.isCollapsed;
                subItem.isCollapsed = !subItem.isCollapsed;
                menuItem.isCollapsed = !menuItem.isCollapsed;
              } else {
                childitem.isCollapsed = true;
              }

              if (childitem.subItems) {
                childitem.subItems.forEach((childrenitem: any) => {
                  if (childrenitem === item) {
                    childrenitem.isCollapsed = false;
                    childitem.isCollapsed = false;
                    subItem.isCollapsed = false;
                    menuItem.isCollapsed = false;
                  } else {
                    childrenitem.isCollapsed = true;
                  }
                });
              }
            });
          }
        });
      }
    });
  }

  activateParentDropdown(item: any) {
    item.classList.add('active');
    const parentCollapseDiv = item.closest('.collapse.menu-dropdown');

    if (parentCollapseDiv) {
      parentCollapseDiv.parentElement.children[0].classList.add('active');

      const parentDropdown = parentCollapseDiv.parentElement.closest('.collapse.menu-dropdown');
      if (parentDropdown) {
        const prev1 = parentCollapseDiv.parentElement.closest('.collapse')?.previousElementSibling as HTMLElement | null;
        prev1?.classList.add('active');

        const prev2 = prev1?.closest?.('.collapse')?.previousElementSibling as HTMLElement | null;
        prev2?.classList.add('active');
      }
      return false;
    }
    return false;
  }

  updateActive(event: any) {
    const ul = document.getElementById('navbar-nav');
    if (ul) {
      const items = Array.from(ul.querySelectorAll('a.nav-link'));
      this.removeActivation(items);
    }
    this.activateParentDropdown(event.target);

    // klik też potrafi zmieniać collapse/klasy → dopnij CD
    this.cdr.detectChanges();
  }

  initActiveMenu() {
    let pathName = window.location.pathname;

    if (environment.production) {
      pathName = pathName.replace('/velzon/angular/default', '');
    }

    const active = this.findMenuItem(pathName, this.menuItems);
    this.toggleItem(active);

    const ul = document.getElementById('navbar-nav');
    if (!ul) return;

    const items = Array.from(ul.querySelectorAll('a.nav-link'));
    const activeItems = items.filter((x: any) => x.classList.contains('active'));
    this.removeActivation(activeItems);

    const matchingMenuItem = items.find((x: any) => {
      if (environment.production) {
        let path = x.pathname;
        path = path.replace('/velzon/angular/default', '');
        return path === pathName;
      }
      return x.pathname === pathName;
    });

    if (matchingMenuItem) {
      this.activateParentDropdown(matchingMenuItem);
    }
  }

  private findMenuItem(pathname: string, menuItems: any[]): any {
    for (const menuItem of menuItems) {
      if (menuItem.link && menuItem.link === pathname) return menuItem;

      if (menuItem.subItems) {
        const foundItem = this.findMenuItem(pathname, menuItem.subItems);
        if (foundItem) return foundItem;
      }
    }
    return null;
  }

  hasItems(item: MenuItem) {
    return item.subItems !== undefined ? item.subItems.length > 0 : false;
  }

  toggleMobileMenu(event: any) {
    const sidebarsize = document.documentElement.getAttribute('data-sidebar-size');
    document.documentElement.setAttribute(
      'data-sidebar-size',
      sidebarsize === 'sm-hover-active' ? 'sm-hover' : 'sm-hover-active'
    );
  }

  SidebarHide() {
    document.body.classList.remove('vertical-sidebar-enable');
  }
}
