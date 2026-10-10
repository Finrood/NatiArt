import {LanguageSelectorComponent} from "../../../../shared/components/language-selector/language-selector.component";
import {AccessibleDialogComponent} from '../../../../shared/components/accessible-dialog.component';
import {afterNextRender, Component, DestroyRef, ElementRef, inject, Signal, signal, OnInit, viewChild, WritableSignal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AsyncPipe, DOCUMENT} from "@angular/common";
import {CartService} from "../../../service/cart.service";
import {Observable} from "rxjs";
import {SavedCollectionService} from '../../../service/saved-collection.service';
import {CartModalComponent} from "../cart-modal/cart-modal.component";
import {AuthenticationService} from "../../../../directory/service/authentication.service";
import {Event, NavigationSkipped, NavigationStart, Router, RouterLink, RouterLinkActive} from "@angular/router";

@Component({
    selector: 'app-top-menu',
    imports: [
    CartModalComponent,
    LanguageSelectorComponent,
    AsyncPipe,
    RouterLink,
    RouterLinkActive,
    AccessibleDialogComponent
],
    templateUrl: './top-menu.component.html',
    styleUrl: './top-menu.component.css'
})
export class TopMenuComponent implements OnInit {
  readonly collection: SavedCollectionService = inject(SavedCollectionService);
  readonly $isLoggedIn: WritableSignal<boolean> = signal(false);
  readonly $isAdmin: WritableSignal<boolean> = signal(false);
  get isLoggedIn(): boolean { return this.$isLoggedIn(); }
  set isLoggedIn(value: boolean) { this.$isLoggedIn.set(value); }
  readonly cartItemCount$: Observable<number>;
  readonly $isCartOpen: WritableSignal<boolean> = signal(false);
  readonly $isMobileMenuOpen: WritableSignal<boolean> = signal(false);

  private readonly _cartService: CartService = inject(CartService);
  private readonly _authService: AuthenticationService = inject(AuthenticationService);
  private readonly _router: Router = inject(Router);
  private readonly _destroyRef: DestroyRef = inject(DestroyRef);
  private readonly _document: Document = inject(DOCUMENT);
  private readonly $headerRow: Signal<ElementRef<HTMLElement>> = viewChild.required<ElementRef<HTMLElement>>('headerRow');

  constructor() {
    afterNextRender((): void => {
      const row: HTMLElement = this.$headerRow().nativeElement;
      const root: HTMLElement = this._document.documentElement;
      const previous: string = root.style.getPropertyValue('--header-clearance');
      const observer: ResizeObserver = new ResizeObserver((): void =>
        root.style.setProperty('--header-clearance', Math.ceil(row.getBoundingClientRect().height + 17) + 'px'));
      observer.observe(row);
      this._destroyRef.onDestroy((): void => {
        observer.disconnect();
        if (previous) root.style.setProperty('--header-clearance', previous);
        else root.style.removeProperty('--header-clearance');
      });
    });
    this.cartItemCount$ = this._cartService.getCartCount();
    this._router.events.pipe(takeUntilDestroyed(this._destroyRef)).subscribe((event: Event): void => {
      if (event instanceof NavigationStart || event instanceof NavigationSkipped) {
        this.closeCartPreview();
        this.closeMobileMenu();
      }
    });
  }

  ngOnInit(): void {
    this._authService.isLoggedIn$.pipe(takeUntilDestroyed(this._destroyRef)).subscribe((isLoggedIn: boolean): void => {
      this.isLoggedIn = isLoggedIn;
      this.$isAdmin.set(isLoggedIn && this._authService.isAdmin === true);
    });
  }

  closeMobileMenu(): void { this.$isMobileMenuOpen.set(false); }

  closeCartPreview(): void {
    this.$isCartOpen.set(false);
  }

  toggleMobileMenu(): void {
    this.closeCartPreview();
    this.$isMobileMenuOpen.update((open: boolean): boolean => !open);
  }

  toggleCartPreview(): void {
    this.closeMobileMenu();
    this.$isCartOpen.update((open: boolean): boolean => !open);
  }

}
