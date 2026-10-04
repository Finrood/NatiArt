import {AccessibleDialogComponent} from '../../../../shared/components/accessible-dialog.component';
import {Component, inject, signal, HostListener, OnDestroy, OnInit} from '@angular/core';
import { AsyncPipe } from "@angular/common";
import {CartService} from "../../../service/cart.service";
import {Observable, Subscription} from "rxjs";
import {CartModalComponent} from "../cart-modal/cart-modal.component";
import {AuthenticationService} from "../../../../directory/service/authentication.service";
import {RouterLink} from "@angular/router";

@Component({
    selector: 'app-top-menu',
    imports: [
    CartModalComponent,
    AsyncPipe,
    RouterLink,
    AccessibleDialogComponent
],
    templateUrl: './top-menu.component.html',
    styleUrl: './top-menu.component.css'
})
export class TopMenuComponent implements OnInit, OnDestroy {
  readonly $isLoggedIn = signal(false);
  get isLoggedIn(): boolean { return this.$isLoggedIn(); }
  set isLoggedIn(value: boolean) { this.$isLoggedIn.set(value); }
  cartItemCount$: Observable<number>;
  isCartHovered = false;
  isMobileMenuOpen = false;

  private authSubscription: Subscription | undefined;
  private cartHoverCloseTimer: ReturnType<typeof setTimeout> | undefined = undefined;

  private readonly _cartService = inject(CartService);
  private readonly _authService = inject(AuthenticationService);

  constructor() {
    this.cartItemCount$ = this._cartService.getCartCount();
  }

  ngOnInit() {
    this.authSubscription = this._authService.isLoggedIn$.subscribe(isLoggedIn => {
      this.isLoggedIn = isLoggedIn;
    });
  }

  ngOnDestroy(): void {
    this.clearCartHoverCloseTimer();
    this.authSubscription?.unsubscribe();
  }

  @HostListener('document:click', ['$event'])
  clickOutside(event: Event) {
    if (!(event.target as HTMLElement).closest('.cart-container')) {
      this.isCartHovered = false;
    }
  }

  closeMobileMenu(): void { this.isMobileMenuOpen = false; }

  toggleMobileMenu(): void {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }

  showCartModal() {
    this.isCartHovered = true;
  }

  hideCartModal(): void {
    // Using setTimeout to allow clicking inside the modal before it closes
    this.clearCartHoverCloseTimer();
    this.cartHoverCloseTimer = setTimeout(() => {
      if (!this.isCartHovered) {
        this.isCartHovered = false;
      }
    }, 200);
  }

  private clearCartHoverCloseTimer(): void {
    if (this.cartHoverCloseTimer !== undefined) {
      clearTimeout(this.cartHoverCloseTimer);
      this.cartHoverCloseTimer = undefined;
    }
  }

}
