import {Component} from '@angular/core';
import {NgIf} from "@angular/common";

@Component({
  selector: 'app-header',
  standalone: true,
    imports: [
        NgIf
    ],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css'
})
export class HeaderComponent {
  isMobileMenuOpen = false;

  toggleMobileMenu() {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }
}
