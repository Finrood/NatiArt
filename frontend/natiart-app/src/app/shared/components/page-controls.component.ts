import {Component, input, output} from '@angular/core';
import {ButtonComponent} from './button.component';

@Component({selector: 'app-page-controls', imports: [ButtonComponent], template: `
  <nav aria-label="Pagination" i18n-aria-label [attr.aria-busy]="$loading()" class="flex flex-wrap items-center gap-3 my-6 text-xs font-sans">
    <app-button type="button" color="light" size="sm" [disabled]="$loading() || $page() === 0"
      (click)="pageChange.emit($page() - 1)" i18n>Previous page</app-button>
    <span aria-live="polite" i18n>Page {{ $page() + 1 }} · {{ $total() }} items</span>
    <app-button type="button" color="light" size="sm" [disabled]="$loading() || !$hasNext()"
      (click)="pageChange.emit($page() + 1)" i18n>Next page</app-button>
    @if ($error()) { <p role="alert">{{ $error() }}</p>
      <app-button type="button" color="light" size="sm" (click)="pageChange.emit($page())" i18n>Retry page</app-button> }
  </nav>`})
export class PageControlsComponent {
  readonly $page = input(0, {alias: 'page'});
  readonly $total = input(0, {alias: 'total'});
  readonly $hasNext = input(false, {alias: 'hasNext'});
  readonly $loading = input(false, {alias: 'loading'});
  readonly $error = input('', {alias: 'error'});
  readonly pageChange = output<number>();
}
