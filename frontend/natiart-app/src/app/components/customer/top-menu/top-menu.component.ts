import { Component } from '@angular/core';
import {NgIf} from "@angular/common";

@Component({
  selector: 'app-top-menu',
  standalone: true,
  imports: [
    NgIf
  ],
  templateUrl: './top-menu.component.html',
  styleUrl: './top-menu.component.css'
})
export class TopMenuComponent {
  isLoggedIn = false; // This should be managed by an AuthService in a real app
  cartItemCount = 0; // This should be managed by a CartService in a real app

  isMobileMenuOpen = false;

  toggleMobileMenu() {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }

  login() {
    // Implement login logic
    this.isLoggedIn = true;
  }

  logout() {
    // Implement logout logic
    this.isLoggedIn = false;
  }

  viewCart() {
    // Implement view cart logic
  }

  search(term: string) {
    // Implement search logic
    console.log('Searching for:', term);
  }
}
