import {Component} from '@angular/core';

import {ButtonComponent} from "../../button.component";
import {RouterLink} from '@angular/router';

@Component({
    selector: 'app-header',
    imports: [
    ButtonComponent,
    RouterLink
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
