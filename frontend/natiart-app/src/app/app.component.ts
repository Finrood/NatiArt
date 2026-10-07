import {afterEveryRender, Component, DestroyRef, ElementRef, HostListener, inject, signal} from '@angular/core';
import {Event, NavigationEnd, Router, RouterOutlet, Scroll} from '@angular/router';
import {ViewportScroller} from '@angular/common';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {TopMenuComponent} from './product/components/customer/top-menu/top-menu.component';
import {LanguageSelectorComponent} from './shared/components/language-selector/language-selector.component';
import {FooterComponent} from "./shared/components/shared/footer/footer.component";
import {AuthenticationService} from "./directory/service/authentication.service";

@Component({
    selector: 'app-root',
  imports: [RouterOutlet, FooterComponent, LanguageSelectorComponent, TopMenuComponent],
    templateUrl: './app.component.html',
    styleUrl: './app.component.css'
})
export class AppComponent {
  readonly title: string = 'NatiArt';

  private readonly _element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly _router = inject(Router);
  private readonly _destroyRef = inject(DestroyRef);
  private readonly _scroller = inject(ViewportScroller);
  private readonly $headingFocusPending = signal(false);
  private readonly $scrollPending = signal<Scroll | null>(null);

  constructor() {
    this._router.events.pipe(
      takeUntilDestroyed(this._destroyRef)
    ).subscribe((event: Event): void => {
      if (event instanceof NavigationEnd) {
        this.$headingFocusPending.set(true);
        this.$scrollPending.set(null);
      } else if (event instanceof Scroll) {
        this.$scrollPending.set(event);
      }
    });
    afterEveryRender((): void => {
      if (!this.$headingFocusPending() && !this.$scrollPending()) return;
      const page: Element | null = this._element.nativeElement.querySelector('router-outlet')?.nextElementSibling ?? null;
      const heading: HTMLElement | undefined = page?.querySelector<HTMLElement>('[data-page-heading]')
        ?? Array.from(page?.querySelectorAll<HTMLElement>('h1, h2') ?? [])
          .find((element: HTMLElement): boolean => element.getClientRects().length > 0);
      if (!heading) return;
      if (this.$headingFocusPending()) {
        heading.setAttribute('tabindex', '-1');
        heading.setAttribute('data-route-heading', '');
        heading.focus({preventScroll: true});
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
