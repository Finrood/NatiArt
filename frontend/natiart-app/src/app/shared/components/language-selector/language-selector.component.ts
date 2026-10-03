import {DOCUMENT} from '@angular/common';
import {Component, inject, LOCALE_ID} from '@angular/core';

export type ShopLanguage = 'en' | 'pt-BR';

export function languageUrl(language: ShopLanguage, pathname: string, search: string, hash: string): string {
  const route: string = pathname.replace(/^\/(?:en|pt-BR)(?=\/|$)/, '').replace(/^\/+/, '');
  return `/${language}/${route}${search}${hash}`;
}

@Component({
  selector: 'app-language-selector',
  template: `
    <nav class="flex flex-wrap justify-end items-center gap-3 px-4 py-2 text-sm" aria-label="Shop language" i18n-aria-label>
      <span i18n>Language</span>
      <a [href]="url('en')" lang="en" hreflang="en" [attr.aria-current]="language === 'en' ? 'true' : null"
         (click)="beforeSwitch($event, 'en')" class="underline underline-offset-4 focus-visible:outline-2">English</a>
      <a [href]="url('pt-BR')" lang="pt-BR" hreflang="pt-BR" [attr.aria-current]="language === 'pt-BR' ? 'true' : null"
         (click)="beforeSwitch($event, 'pt-BR')" class="underline underline-offset-4 focus-visible:outline-2">Português</a>
    </nav>
  `
})
export class LanguageSelectorComponent {
  private readonly _document = inject(DOCUMENT);
  readonly language: string = inject(LOCALE_ID);

  url(language: ShopLanguage): string {
    const location: Location | undefined = this._document.defaultView?.location;
    return languageUrl(language, location?.pathname ?? '/', location?.search ?? '', location?.hash ?? '');
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
