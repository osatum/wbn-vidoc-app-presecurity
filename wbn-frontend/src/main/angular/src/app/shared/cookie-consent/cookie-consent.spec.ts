import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbOffcanvas } from '@ng-bootstrap/ng-bootstrap';
import { CookieService } from 'ngx-cookie-service';

import { translocoTestingModule } from '../../testing/transloco-testing-module';
import { CookieConsent } from './cookie-consent';

describe('CookieConsent', () => {
  let component: CookieConsent;
  let fixture: ComponentFixture<CookieConsent>;
  let cookies: { get: ReturnType<typeof vi.fn>; set: ReturnType<typeof vi.fn> };
  let offcanvas: { open: ReturnType<typeof vi.fn> };
  let offcanvasRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    cookies = {
      get: vi.fn(() => ''),
      set: vi.fn(),
    };
    offcanvasRef = {
      close: vi.fn(),
    };
    offcanvas = {
      open: vi.fn(() => offcanvasRef),
    };

    await TestBed.configureTestingModule({
      imports: [CookieConsent, translocoTestingModule],
      providers: [
        { provide: CookieService, useValue: cookies },
        { provide: NgbOffcanvas, useValue: offcanvas },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CookieConsent);
    component = fixture.componentInstance;
  });

  it('should open consent offcanvas when consent cookie is missing', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    expect(offcanvas.open).toHaveBeenCalledWith(expect.anything(), {
      position: 'bottom',
      backdrop: false,
      keyboard: false,
      scroll: true,
      panelClass: 'cookie-consent-offcanvas',
    });
  });

  it('should not open consent offcanvas when consent cookie is accepted', async () => {
    cookies.get.mockReturnValue('accepted');

    fixture.detectChanges();
    await fixture.whenStable();

    expect(offcanvas.open).not.toHaveBeenCalled();
  });

  it('should save consent cookie and close offcanvas on accept', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    component.accept();

    expect(cookies.set).toHaveBeenCalledWith('jml_cookie_consent', 'accepted', {
      expires: 365,
      path: '/',
      sameSite: 'Lax',
    });
    expect(offcanvasRef.close).toHaveBeenCalled();
  });
});
