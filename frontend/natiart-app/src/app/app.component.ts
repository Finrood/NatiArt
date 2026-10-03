import {Component, HostListener, inject} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {LanguageSelectorComponent} from './shared/components/language-selector/language-selector.component';
import {FooterComponent} from "./shared/components/shared/footer/footer.component";
import {AuthenticationService} from "./directory/service/authentication.service";

@Component({
    selector: 'app-root',
  imports: [RouterOutlet, FooterComponent, LanguageSelectorComponent],
    templateUrl: './app.component.html',
    styleUrl: './app.component.css'
})
export class AppComponent {
  readonly title: string = 'NatiArt';

  isMobileMenuOpen = false;

  private readonly _authenticationService = inject(AuthenticationService);

  @HostListener('document:mousemove')
  @HostListener('document:keydown')
  @HostListener('document:touchstart')
  onUserActivity() {
    this._authenticationService.resetInactivityTimer();
  }

  toggleMobileMenu() {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }
}
