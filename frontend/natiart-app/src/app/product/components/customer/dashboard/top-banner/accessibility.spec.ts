import {TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {TopBannerComponent} from './top-banner.component';

describe('Single-image storefront hero', () => {
  it('exposes a direct collection link and no carousel controls or automatic motion', async () => {
    await TestBed.configureTestingModule({imports: [TopBannerComponent], providers: [provideRouter([])]}).compileComponents();
    const timer: jasmine.Spy = spyOn(window, 'setInterval');
    const fixture = TestBed.createComponent(TopBannerComponent);
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('a[href="/products"]')?.textContent).toContain('Explore Collections');
    expect(root.querySelector('button')).toBeNull();
    expect(root.querySelector('[aria-roledescription="carousel"]')).toBeNull();
    expect(root.querySelector('img')?.getAttribute('width')).toBe('1580');
    expect(timer).not.toHaveBeenCalled();
    fixture.destroy();
  });
});
