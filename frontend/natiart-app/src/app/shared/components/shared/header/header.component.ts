import {Component} from '@angular/core';
import {NgIf} from "@angular/common";
import {ButtonComponent} from "../button.component";

@Component({
    selector: 'app-header',
    imports: [
        NgIf,
        ButtonComponent
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
