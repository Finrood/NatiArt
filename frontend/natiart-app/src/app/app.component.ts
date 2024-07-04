import { Component } from '@angular/core';
import {RouterLink, RouterOutlet} from '@angular/router';
import {NgIf} from "@angular/common";
import {FooterComponent} from "./components/shared/footer/footer.component";
import {HeaderComponent} from "./components/shared/header/header.component";

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, FooterComponent, NgIf, HeaderComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'authentication-app';

  isMobileMenuOpen = false;

  toggleMobileMenu() {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }
}
