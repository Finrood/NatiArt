import {DOCUMENT} from '@angular/common';
import {Component, inject, LOCALE_ID, Signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {Event, NavigationEnd, Router} from '@angular/router';
import {filter, map} from 'rxjs';

export type ShopLanguage = 'en' | 'pt-BR';

export function languageUrl(language: ShopLanguage, pathname: string, search: string, hash: string): string {
  const route: string = pathname.replace(/^\/(?:en|pt-BR)(?=\/|$)/, '').replace(/^\/+/, '');
  return `/${language}/${route}${search}${hash}`;
}

@Component({
  selector: 'app-language-selector',
  template: `
    <nav class="flex flex-wrap items-center gap-3 text-xs font-sans" aria-label="Shop language" i18n-aria-label>
      <span class="sr-only" i18n>Language</span>
      <a [href]="url('en')" lang="en" hreflang="en" [attr.aria-current]="language === 'en' ? 'true' : null"
         (click)="beforeSwitch($event, 'en')" class="inline-flex min-h-11 items-center underline underline-offset-4 focus-visible:outline-2">English</a>
      <a [href]="url('pt-BR')" lang="pt-BR" hreflang="pt-BR" [attr.aria-current]="language === 'pt-BR' ? 'true' : null"
         (click)="beforeSwitch($event, 'pt-BR')" class="inline-flex min-h-11 items-center underline underline-offset-4 focus-visible:outline-2">Português</a>
    </nav>
  `
})
export class LanguageSelectorComponent {
  private readonly _document = inject(DOCUMENT);
  private readonly _router: Router = inject(Router);
  private readonly $currentUrl: Signal<string> = toSignal(this._router.events.pipe(
    filter((event: Event): event is NavigationEnd => event instanceof NavigationEnd),
    map((event: NavigationEnd): string => event.urlAfterRedirects),
  ), {initialValue: this.initialUrl()});
  readonly language: string = inject(LOCALE_ID);

  private initialUrl(): string {
    const location: Location | undefined = this._document.defaultView?.location;
    return (location?.pathname ?? '/') + (location?.search ?? '') + (location?.hash ?? '');
  }

  url(language: ShopLanguage): string {
    return languageUrl(language, this.$currentUrl(), '', '');
  }

  beforeSwitch(event: MouseEvent, language: ShopLanguage): void {
    if (language === this.language) {
      event.preventDefault();
      return;
    }
    // Localized bundles reload the page. Never silently discard an edited form.
    if (this._document.querySelector('form.ng-dirty')
        && !this._document.defaultView?.confirm($localize`Changing language reloads this page. Unsaved form entries may be lost. Continue?`)) {
      event.preventDefault();
    }
  }
}
