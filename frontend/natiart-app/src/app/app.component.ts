import {afterEveryRender, Component, DestroyRef, ElementRef, HostListener, inject, signal} from '@angular/core';
import {Event, NavigationEnd, Router, RouterOutlet} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {filter} from 'rxjs';
import {TopMenuComponent} from './product/components/customer/top-menu/top-menu.component';
import {LanguageSelectorComponent} from './shared/components/language-selector/language-selector.component';
import {FooterComponent} from "./shared/components/shared/footer/footer.component";
import {AuthenticationService} from "./directory/service/authentication.service";

@Component({
    selector: 'app-root',
  imports: [RouterOutlet, FooterComponent, LanguageSelectorComponent, TopMenuComponent],
    templateUrl: './app.component.html',
    styleUrl: './app.component.css'
})
export class AppComponent {
  readonly title: string = 'NatiArt';

  private readonly _element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly _router = inject(Router);
  private readonly _destroyRef = inject(DestroyRef);
  private readonly $headingFocusPending = signal(false);

  constructor() {
    this._router.events.pipe(
      filter((event: Event): event is NavigationEnd => event instanceof NavigationEnd),
      takeUntilDestroyed(this._destroyRef)
    ).subscribe((): void => this.$headingFocusPending.set(true));
    afterEveryRender((): void => {
      if (!this.$headingFocusPending()) return;
      const page: Element | null = this._element.nativeElement.querySelector('router-outlet')?.nextElementSibling ?? null;
      const heading: HTMLElement | undefined = page?.querySelector<HTMLElement>('[data-page-heading]')
        ?? Array.from(page?.querySelectorAll<HTMLElement>('h1, h2') ?? [])
          .find((element: HTMLElement): boolean => element.getClientRects().length > 0);
      if (!heading) return;
      heading.setAttribute('tabindex', '-1');
      heading.focus({preventScroll: true});
      this.$headingFocusPending.set(false);
    });
  }

  private readonly _authenticationService = inject(AuthenticationService);

  @HostListener('document:mousemove')
  @HostListener('document:keydown')
  @HostListener('document:touchstart')
  onUserActivity() {
    this._authenticationService.resetInactivityTimer();
  }

}
