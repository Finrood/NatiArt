import {afterEveryRender, Component, DestroyRef, ElementRef, HostListener, inject, signal, WritableSignal} from '@angular/core';
import {Event, NavigationEnd, NavigationError, Router, RouterOutlet, Scroll} from '@angular/router';
import {Location, ViewportScroller} from '@angular/common';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {TopMenuComponent} from './product/components/customer/top-menu/top-menu.component';
import {FooterComponent} from "./shared/components/shared/footer/footer.component";
import {AuthenticationService} from "./directory/service/authentication.service";
import {ProductComparisonComponent} from './shared/components/product-comparison/product-comparison.component';
import {ProductComparisonService} from './product/service/product-comparison.service';

@Component({
    selector: 'app-root',
  imports: [RouterOutlet, FooterComponent, TopMenuComponent, ProductComparisonComponent],
    templateUrl: './app.component.html',
    styleUrl: './app.component.css'
})
export class AppComponent {
  readonly title: string = 'NatiArt';
  readonly comparison: ProductComparisonService = inject(ProductComparisonService);

  private readonly _element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly _router = inject(Router);
  private readonly _destroyRef = inject(DestroyRef);
  private readonly _scroller = inject(ViewportScroller);
  private readonly _location: Location = inject(Location);
  private readonly $routeUrl: WritableSignal<string> = signal(this._router.url);
  private readonly $headingFocusPending = signal(false);
  private readonly $catalogSearchFocusPending = signal(false);
  private readonly $scrollPending = signal<Scroll | null>(null);
  readonly $navigationError: WritableSignal<boolean> = signal(false);
  private readonly $navigationErrorFocusPending: WritableSignal<boolean> = signal(false);

  reloadPage(): void { window.location.reload(); }

  get skipContentHref(): string {
    return `${this._location.prepareExternalUrl(this.$routeUrl().split('#')[0])}#store-content`;
  }

  skipToContent(event: MouseEvent): void {
    event.preventDefault();
    this.$headingFocusPending.set(false);
    this.$catalogSearchFocusPending.set(false);
    this.$scrollPending.set(null);
    const content: HTMLElement | null = this._element.nativeElement.querySelector('#store-content');
    content?.focus({preventScroll: true});
    this._scroller.scrollToAnchor('store-content');
  }

  constructor() {
    this._router.events.pipe(
      takeUntilDestroyed(this._destroyRef)
    ).subscribe((event: Event): void => {
      if (event instanceof NavigationEnd) {
        this.$navigationError.set(false);
        this.$navigationErrorFocusPending.set(false);
        this.$routeUrl.set(event.urlAfterRedirects);
        this.$headingFocusPending.set(true);
        this.$catalogSearchFocusPending.set(this._router.currentNavigation()?.extras.info === 'catalog-search');
        this.$scrollPending.set(null);
      } else if (event instanceof NavigationError) {
        this.$headingFocusPending.set(false);
        this.$catalogSearchFocusPending.set(false);
        this.$scrollPending.set(null);
        this.$navigationError.set(true);
        this.$navigationErrorFocusPending.set(true);
      } else if (event instanceof Scroll) {
        this.$scrollPending.set(event);
      }
    });
    afterEveryRender((): void => {
      if (this.$navigationErrorFocusPending()) {
        this._element.nativeElement.querySelector<HTMLElement>('[data-navigation-error]')?.focus({preventScroll: true});
        this._scroller.scrollToPosition([0, 0]);
        this.$navigationErrorFocusPending.set(false);
      }
      if (!this.$headingFocusPending() && !this.$scrollPending()) return;
      const page: Element | null = this._element.nativeElement.querySelector('router-outlet')?.nextElementSibling ?? null;
      const heading: HTMLElement | undefined = page?.querySelector<HTMLElement>('[data-page-heading]')
        ?? Array.from(page?.querySelectorAll<HTMLElement>('h1, h2') ?? [])
          .find((element: HTMLElement): boolean => element.getClientRects().length > 0);
      if (!heading) return;
      if (this.$headingFocusPending()) {
        const search: HTMLInputElement | null = this.$catalogSearchFocusPending()
          ? page?.querySelector<HTMLInputElement>('#catalog-search') ?? null : null;
        if (search) search.focus({preventScroll: true});
        else {
          heading.setAttribute('tabindex', '-1');
          heading.setAttribute('data-route-heading', '');
          heading.focus({preventScroll: true});
        }
        this.$catalogSearchFocusPending.set(false);
        this.$headingFocusPending.set(false);
      }
      const scroll: Scroll | null = this.$scrollPending();
      if (!scroll || page?.matches('[aria-busy="true"]') || page?.querySelector('[aria-busy="true"]')) return;
      if (scroll.position) this._scroller.scrollToPosition(scroll.position);
      else if (scroll.anchor) this._scroller.scrollToAnchor(scroll.anchor);
      else this._scroller.scrollToPosition([0, 0]);
      this.$scrollPending.set(null);
    });
  }

  private readonly _authenticationService = inject(AuthenticationService);

  @HostListener('document:mousemove')
  @HostListener('document:keydown')
  @HostListener('document:touchstart')
  onUserActivity() {
    this._authenticationService.resetInactivityTimer();
  }

}
