import {Component, OnDestroy, OnInit, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {ButtonComponent} from '../../../../../shared/components/button.component';
import {NgClass} from '@angular/common';

@Component({selector: 'app-top-banner', imports: [ButtonComponent, NgClass, RouterLink],
  templateUrl: './top-banner.component.html', styleUrl: './top-banner.component.css'})
export class TopBannerComponent implements OnInit, OnDestroy {
  readonly $currentBannerIndex = signal(0);
  readonly $paused = signal(false);
  readonly $reducedMotion = signal(false);
  get currentBannerIndex(): number { return this.$currentBannerIndex(); }
  set currentBannerIndex(value: number) { this.$currentBannerIndex.set(value); }
  readonly bannerImages: string[] = ['assets/img/a1.webp'];
  bannerLabel(index: number): string {
    return $localize`Go to banner ${index}:IMAGE_NUMBER:`;
  }

  get rotationLabel(): string { return this.$reducedMotion() ? $localize`Rotation off (reduced motion)` : this.$paused() ? $localize`Resume rotation` : $localize`Pause rotation`; }

  private bannerInterval: ReturnType<typeof setInterval> | undefined;
  private media: MediaQueryList | null = null;
  private pointerInside: boolean = false;
  private focusInside: boolean = false;
  private readonly motionChanged = (event: MediaQueryListEvent): void => {
    this.$reducedMotion.set(event.matches);
    this.resetBannerInterval();
  };

  ngOnInit(): void {
    this.media = window.matchMedia('(prefers-reduced-motion: reduce)');
    this.$reducedMotion.set(this.media.matches);
    this.media.addEventListener('change', this.motionChanged);
    this.resetBannerInterval();
  }
  ngOnDestroy(): void { this.stopBannerInterval(); this.media?.removeEventListener('change', this.motionChanged); }
  prevSlide(): void { this.currentBannerIndex = (this.currentBannerIndex - 1 + this.bannerImages.length) % this.bannerImages.length; this.resetBannerInterval(); }
  nextSlide(): void { this.currentBannerIndex = (this.currentBannerIndex + 1) % this.bannerImages.length; this.resetBannerInterval(); }
  goToSlide(index: number): void { this.currentBannerIndex = index; this.resetBannerInterval(); }
  toggleRotation(): void { this.$paused.update((paused: boolean): boolean => !paused); this.resetBannerInterval(); }
  pointer(inside: boolean): void { this.pointerInside = inside; this.resetBannerInterval(); }
  focus(event: FocusEvent, inside: boolean): void {
    this.focusInside = inside || (!!event.relatedTarget && (event.currentTarget as HTMLElement).contains(event.relatedTarget as Node));
    this.resetBannerInterval();
  }
  keyboard(event: Event): void {
    const key = (event as KeyboardEvent).key;
    if (key === 'ArrowLeft') this.prevSlide();
    else if (key === 'ArrowRight') this.nextSlide();
    else if (key === 'Home') this.goToSlide(0);
    else if (key === 'End') this.goToSlide(this.bannerImages.length - 1);
    else return;
    event.preventDefault();
  }
  private stopBannerInterval(): void { if (this.bannerInterval !== undefined) clearInterval(this.bannerInterval); this.bannerInterval = undefined; }
  private resetBannerInterval(): void {
    this.stopBannerInterval();
    if (this.$paused() || this.$reducedMotion() || this.pointerInside || this.focusInside) return;
    this.bannerInterval = setInterval((): void => {
      this.currentBannerIndex = (this.currentBannerIndex + 1) % this.bannerImages.length;
    }, 4500);
  }
}
