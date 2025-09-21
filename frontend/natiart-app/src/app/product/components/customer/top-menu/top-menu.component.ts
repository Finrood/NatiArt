import {Component, HostListener, OnDestroy, OnInit} from '@angular/core';
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
    RouterLink
],
    templateUrl: './top-menu.component.html',
    styleUrl: './top-menu.component.css'
})
export class TopMenuComponent implements OnInit, OnDestroy {
  isLoggedIn = false;
  cartItemCount$: Observable<number>;
  isCartHovered = false;
  isMobileMenuOpen = false;

  private authSubscription: Subscription | undefined;

  constructor(
    private cartService: CartService,
    private authService: AuthenticationService
  ) {
    this.cartItemCount$ = this.cartService.getCartCount();
  }

  ngOnInit() {
    this.authSubscription = this.authService.isLoggedIn$.subscribe(isLoggedIn => {
      this.isLoggedIn = isLoggedIn;
    });
  }

  ngOnDestroy() {
    this.authSubscription?.unsubscribe();
  }

  @HostListener('document:click', ['$event'])
  clickOutside(event: Event) {
    if (!(event.target as HTMLElement).closest('.cart-container')) {
      this.isCartHovered = false;
    }
  }

  toggleMobileMenu() {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }

  showCartModal() {
    this.isCartHovered = true;
  }

  hideCartModal() {
    // Using setTimeout to allow clicking inside the modal before it closes
    setTimeout(() => {
      if (!this.isCartHovered) {
        this.isCartHovered = false;
      }
    }, 200);
  }

  search(term: string) {
    console.log('Searching for:', term);
    //TODO
  }
}
