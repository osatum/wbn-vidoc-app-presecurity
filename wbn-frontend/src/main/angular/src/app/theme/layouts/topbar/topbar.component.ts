import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  inject,
  OnInit,
  Output,
  TemplateRef,
  ViewChild,
} from '@angular/core';
import { DOCUMENT, NgClass } from '@angular/common';
import { Router } from '@angular/router';

import {
  NgbDropdown,
  NgbDropdownModule,
  NgbModal,
  NgbModule,
  NgbNav,
  NgbNavItem,
  NgbNavOutlet,
} from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { SimplebarAngularModule } from 'simplebar-angular';

import { TranslocoService } from '@jsverse/transloco';
import { CookieService } from 'ngx-cookie-service';

import { LanguageService } from '../../core/services/language.service';
import { TokenStorageService } from '../../core/services/token-storage.service';

import { allNotification, cartData, messages as messagesData } from './data';
import { CartModel } from './topbar.model';
import { AuthService } from '../../../core/services/auth.service';
import { EventService } from '../../core/services/event.service';

type LangItem = { text: string; flag: string; lang: string };
type ThemeMode = 'light' | 'dark';

@Component({
  selector: 'app-topbar',
  templateUrl: './topbar.component.html',
  styleUrls: ['./topbar.component.scss'],
  imports: [
    FormsModule,
    NgbNavOutlet,
    NgbNavItem,
    SimplebarAngularModule,
    NgbNav,
    NgClass,
    NgbDropdown,
    NgbDropdownModule,
    NgbModule,
  ],
  standalone: true,
  // domyślnie Default, ale jawnie zostawiam
  changeDetection: ChangeDetectionStrategy.Default,
})
export class TopbarComponent implements OnInit {
  // ====== properties used in HTML (initialized to avoid NG0100) ======
  valueset: string = 'assets/images/flags/us.svg';
  messages: any[] = []; // used in HTML
  allnotifications: any[] = []; // used in HTML
  userData: any = null; // used in HTML

  // ====== other state ======
  element!: HTMLElement;
  mode: ThemeMode = 'light';

  @Output() mobileMenuButtonClicked = new EventEmitter<void>();

  flagvalue: string = 'assets/images/flags/us.svg';
  countryName: string = 'English';
  cookieValue: string = 'en';

  cartData: CartModel[] = [];
  total = 0;
  cart_length = 0;

  totalNotify = 0;
  newNotify = 0;
  readNotify = 0;

  isDropdownOpen = false;

  @ViewChild('removenotification') removenotification!: TemplateRef<any>;
  notifyId: '1' | '2' | null = null;

  checkedValGet: Array<string | number> = [];

  protected readonly document = inject<Document>(DOCUMENT);
  protected readonly eventService = inject(EventService);
  public readonly languageService = inject(LanguageService);
  private readonly modalService = inject(NgbModal);
  protected readonly cookies = inject(CookieService);
  public readonly translate = inject(TranslocoService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly tokenStorage = inject(TokenStorageService);
  private readonly cdr = inject(ChangeDetectorRef);

  ngOnInit(): void {
    this.element = this.document.documentElement;

    // 1) user data (init value is null, so template is safe)
    this.userData = this.tokenStorage.getUser();

    // 2) language init from cookie (safe defaults already set)
    this.initLanguageFromCookie();

    // 3) data used in HTML - assign once (safe defaults are [])
    this.allnotifications = allNotification ?? [];
    this.messages = messagesData ?? [];

    // 4) cart
    this.cartData = cartData ?? [];
    this.cart_length = this.cartData.length;
    this.total = this.cartData.reduce((acc, item) => acc + item.quantity * item.price, 0);

    // 5) notification counters (optional, but keeps UI consistent)
    this.calculatenotification();

    // 6) Option 2 “stabilizer” for dev mode NG0100
    this.cdr.detectChanges();
  }

  protected initLanguageFromCookie(): void {
    const cookieLang = this.cookies.get('lang') || 'en';
    this.cookieValue = cookieLang;

    const found = this.listLang.find((x) => x.lang === cookieLang);

    if (found) {
      this.countryName = found.text;
      this.flagvalue = found.flag;
      this.valueset = found.flag; // src used in HTML
    } else {
      // keep defaults
      this.countryName = 'English';
      this.flagvalue = 'assets/images/flags/us.svg';
      this.valueset = this.flagvalue;
      this.cookieValue = 'en';
    }
  }

  /**
   * Toggle the menu bar when having mobile screen
   */
  toggleMobileMenu(event: Event): void {
    event.preventDefault();
    this.document.querySelector('.hamburger-icon')?.classList.toggle('open');
    this.mobileMenuButtonClicked.emit();
  }

  /**
   * Fullscreen method
   */
  fullscreen(): void {
    this.document.body.classList.toggle('fullscreen-enable');

    const doc: any = this.document as any;
    const el: any = this.element as any;

    const isFull =
      this.document.fullscreenElement || el.mozFullScreenElement || el.webkitFullscreenElement;

    if (!isFull) {
      if (el.requestFullscreen) el.requestFullscreen();
      else if (el.mozRequestFullScreen) el.mozRequestFullScreen();
      else if (el.webkitRequestFullscreen) el.webkitRequestFullscreen();
      else if (el.msRequestFullscreen) el.msRequestFullscreen();
    } else {
      if (doc.exitFullscreen) doc.exitFullscreen();
      else if (doc.mozCancelFullScreen) doc.mozCancelFullScreen();
      else if (doc.webkitExitFullscreen) doc.webkitExitFullscreen();
      else if (doc.msExitFullscreen) doc.msExitFullscreen();
    }
  }

  /**
   * Open modal
   */
  openModal(content: any): void {
    this.modalService.open(content, { centered: true });
  }

  /**
   * Topbar Light-Dark Mode Change
   */
  changeMode(mode: ThemeMode): void {
    this.mode = mode;
    this.eventService.broadcast('changeMode', mode);
    this.document.documentElement.setAttribute('data-bs-theme', mode);
  }

  /***
   * Language Listing
   */
  listLang: LangItem[] = [
    { text: 'English', flag: 'assets/images/flags/gb.svg', lang: 'en' },
    { text: 'Polski', flag: 'assets/images/flags/pl.svg', lang: 'pl' }
  ];

  /***
   * Language Value Set
   */
  setLanguage(text: string, lang: string, flag: string): void {
    this.countryName = text;
    this.flagvalue = flag;
    this.valueset = flag;
    this.cookieValue = lang;

    // keep cookie in sync (if your LanguageService already does it, this is still ok)
    this.cookies.set('lang', lang);

    this.languageService.setLanguage(lang);
  }

  /**
   * Logout the user
   */
  logout(): void {
    this.authService.logout();
    this.router.navigate(['/auth/login']);
  }

  windowScroll(): void {
    const backToTop = this.document.getElementById('back-to-top') as HTMLElement | null;
    const topbar = this.document.getElementById('page-topbar');

    const scrolled =
      this.document.body.scrollTop > 100 || this.document.documentElement.scrollTop > 100;

    if (backToTop) backToTop.style.display = scrolled ? 'block' : 'none';
    topbar?.classList.toggle('topbar-shadow', scrolled);
  }

  // Delete Item
  deleteItem(event: Event, id: string | number): void {
    const target = event.target as HTMLElement | null;
    const dropdownItem = target?.closest('.dropdown-item') as HTMLElement | null;
    const priceEl = dropdownItem?.querySelector('.item_price') as HTMLElement | null;

    const price = priceEl ? Number(priceEl.innerHTML) : 0;

    this.total = this.total - price;
    this.cart_length = Math.max(0, this.cart_length - 1);

    const emptyCart = this.document.getElementById('empty-cart') as HTMLElement | null;
    if (emptyCart) emptyCart.style.display = this.total > 1 ? 'none' : 'block';

    this.document.getElementById('item_' + id)?.remove();
  }

  toggleDropdown(event: Event): void {
    event.stopPropagation();
    this.isDropdownOpen = !this.isDropdownOpen;
  }

  // Search Topbar
  Search(): void {
    const searchOptions = this.document.getElementById('search-close-options');
    const dropdown = this.document.getElementById('search-dropdown');
    const input = this.document.getElementById('search-options') as HTMLInputElement | null;

    if (!searchOptions || !dropdown || !input) return;

    const filter = input.value.toUpperCase();
    const inputLength = filter.length;

    if (inputLength > 0) {
      dropdown.classList.add('show');
      searchOptions.classList.remove('d-none');

      const notifyItem = this.document.getElementsByClassName('notify-item');

      Array.from(notifyItem).forEach((element: any) => {
        let notifiTxt = '';

        const h6 = element.querySelector?.('h6') as HTMLElement | null;
        const span = element.getElementsByTagName?.('span')?.[0] as HTMLElement | undefined;

        if (h6 && span) {
          const spantext = span.innerText.toLowerCase();
          const name = h6.innerText.toLowerCase();
          notifiTxt = name.includes(filter.toLowerCase()) ? name : spantext;
        } else if (span) {
          notifiTxt = span.innerText.toLowerCase();
        }

        element.style.display = notifiTxt.includes(filter.toLowerCase()) ? 'block' : 'none';
      });
    } else {
      dropdown.classList.remove('show');
      searchOptions.classList.add('d-none');
    }
  }

  /**
   * Search Close Btn
   */
  closeBtn(): void {
    const searchOptions = this.document.getElementById('search-close-options');
    const dropdown = this.document.getElementById('search-dropdown');
    const input = this.document.getElementById('search-options') as HTMLInputElement | null;

    dropdown?.classList.remove('show');
    searchOptions?.classList.add('d-none');
    if (input) input.value = '';
  }

  // Remove Notification
  onCheckboxChange(event: any, id: '1' | '2'): void {
    this.notifyId = id;

    const source = id === '1' ? this.allnotifications : this.messages;
    const checkedVal: Array<string | number> = [];

    for (let i = 0; i < source.length; i++) {
      if (source[i].state === true) checkedVal.push(source[i].id);
    }

    this.checkedValGet = checkedVal;

    const actions = this.document.getElementById('notification-actions') as HTMLElement | null;
    if (actions) actions.style.display = checkedVal.length > 0 ? 'block' : 'none';
  }

  notificationDelete(): void {
    if (!this.notifyId) return;

    const source = this.notifyId === '1' ? this.allnotifications : this.messages;

    this.checkedValGet.forEach((id) => {
      const idx = source.findIndex((x: any) => x.id === id);
      if (idx >= 0) source.splice(idx, 1);
    });

    this.calculatenotification();
    this.modalService.dismissAll();
  }

  calculatenotification(): void {
    // jeśli masz dokładną logikę liczenia, możesz tu uzupełnić.
    // Na razie utrzymujemy spójny stan UI
    this.totalNotify = 0;
    this.checkedValGet = [];

    const actions = this.document.getElementById('notification-actions') as HTMLElement | null;
    if (actions) actions.style.display = 'none';

    if (this.totalNotify === 0) {
      this.document.querySelector('.empty-notification-elem')?.classList.remove('d-none');
    }
  }
}
