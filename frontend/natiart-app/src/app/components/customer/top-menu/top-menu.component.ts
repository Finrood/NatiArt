import {Component, HostListener, OnInit} from '@angular/core';
import {AsyncPipe, NgIf} from "@angular/common";
import {CartService} from "../../../service/cart.service";
import {Observable} from "rxjs";
import {CartModalComponent} from "../cart-modal/cart-modal.component";

@Component({
  selector: 'app-top-menu',
  standalone: true,
  imports: [
    NgIf,
    CartModalComponent,
    AsyncPipe
  ],
  templateUrl: './top-menu.component.html',
  styleUrl: './top-menu.component.css'
})
export class TopMenuComponent implements OnInit {
  isLoggedIn = false;
  cartItemCount$: Observable<number>;
  isCartHovered = false;
  isMobileMenuOpen = false;

  constructor(private cartService: CartService) {
    this.cartItemCount$ = this.cartService.getCartCount();
  }

  ngOnInit() {
    // Initialize any necessary data
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

  login() {
    this.isLoggedIn = true;
  }

  logout() {
    this.isLoggedIn = false;
  }

  search(term: string) {
    console.log('Searching for:', term);
    //TODO
  }
}
