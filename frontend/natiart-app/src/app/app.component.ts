import {Component, HostListener} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {FooterComponent} from "./shared/components/shared/footer/footer.component";
import {AuthenticationService} from "./directory/service/authentication.service";

@Component({
    selector: 'app-root',
  imports: [RouterOutlet, FooterComponent, FooterComponent],
    templateUrl: './app.component.html',
    styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'authentication-app';

  isMobileMenuOpen = false;

  constructor(private authenticationService: AuthenticationService) {}

  @HostListener('document:mousemove')
  @HostListener('document:keydown')
  @HostListener('document:touchstart')
  onUserActivity() {
    this.authenticationService.resetInactivityTimer();
  }

  toggleMobileMenu() {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }
}
