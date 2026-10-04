import {fakeAsync, TestBed, tick} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {TopBannerComponent} from './top-banner.component';

describe('Carousel keyboard and motion contract', () => {
  beforeEach(async () => {await TestBed.configureTestingModule({imports: [TopBannerComponent], providers: [provideRouter([])]}).compileComponents();});
  it('keeps named controls visible, pauses rotation and accepts keyboard navigation', fakeAsync(() => {
    const fixture = TestBed.createComponent(TopBannerComponent);
    fixture.componentInstance.bannerImages.splice(0, 1, ...Array<string>(4).fill('assets/img/a1.webp'));
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement.querySelector('[aria-roledescription="carousel"]');
    const next: HTMLButtonElement = fixture.nativeElement.querySelector('button[aria-label="Next banner"]');
    expect(getComputedStyle(next).opacity).toBe('1');
    root.dispatchEvent(new KeyboardEvent('keydown', {key: 'ArrowRight', bubbles: true}));
    fixture.detectChanges();
    expect(fixture.componentInstance.currentBannerIndex).toBe(1);
    const pause: HTMLButtonElement = fixture.nativeElement.querySelector('button[aria-pressed]');
    pause.click();
    fixture.detectChanges();
    expect(pause.getAttribute('aria-pressed')).toBe('true');
    tick(10000);
    expect(fixture.componentInstance.currentBannerIndex).toBe(1);
    root.dispatchEvent(new KeyboardEvent('keydown', {key: 'End', bubbles: true}));
    fixture.detectChanges();
    expect(fixture.componentInstance.currentBannerIndex).toBe(3);
    expect(fixture.nativeElement.querySelector('button[aria-current="true"]').getAttribute('aria-label')).toBe('Go to banner 4');
    fixture.destroy();
  }));
  it('disables automatic motion for the platform preference and removes its listener on teardown', fakeAsync(() => {
    const remove = jasmine.createSpy('remove');
    spyOn(window, 'matchMedia').and.returnValue({matches: true, addEventListener: jasmine.createSpy('add'), removeEventListener: remove} as unknown as MediaQueryList);
    const fixture = TestBed.createComponent(TopBannerComponent);
    fixture.detectChanges();
    tick(20000);
    expect(fixture.componentInstance.currentBannerIndex).toBe(0);
    expect(fixture.nativeElement.querySelector('button[aria-pressed]').disabled).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Rotation off (reduced motion)');
    fixture.destroy();
    expect(remove).toHaveBeenCalled();
  }));
});
