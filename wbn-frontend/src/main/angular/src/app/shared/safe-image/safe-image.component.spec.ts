import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SafeImageComponent } from './safe-image.component';

describe(SafeImageComponent.name, () => {
  let fixture: ComponentFixture<SafeImageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SafeImageComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(SafeImageComponent);
    fixture.componentRef.setInput('src', '/api/images/product-1.jpg');
    fixture.componentRef.setInput('alt', 'Produkt testowy');
    fixture.detectChanges();
  });

  it('should show loader before image is loaded', () => {
    const loader = fixture.nativeElement.querySelector('.safe-image__loader');

    expect(loader).not.toBeNull();
  });

  it('should mark image as loaded after load event', () => {
    const img: HTMLImageElement | null = fixture.nativeElement.querySelector('img');

    expect(img).not.toBeNull();

    img?.dispatchEvent(new Event('load'));
    fixture.detectChanges();

    const wrapper = fixture.nativeElement.querySelector('.safe-image');

    expect(wrapper.classList).toContain('safe-image--loaded');
    expect(fixture.nativeElement.querySelector('.safe-image__loader')).toBeNull();
  });

  it('should show fallback after image error', () => {
    const img: HTMLImageElement | null = fixture.nativeElement.querySelector('img');

    expect(img).not.toBeNull();

    img?.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.querySelector('.safe-image__fallback')).not.toBeNull();
  });

  it('should show fallback when src is empty', () => {
    fixture.componentRef.setInput('src', '');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.querySelector('.safe-image__fallback')).not.toBeNull();
  });
});
