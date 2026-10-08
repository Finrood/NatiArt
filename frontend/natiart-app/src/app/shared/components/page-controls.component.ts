import {Component, input, output} from '@angular/core';

@Component({selector: 'app-page-controls', template: `
  <nav aria-label="Pagination" i18n-aria-label [attr.aria-busy]="$loading()" class="flex flex-wrap items-center gap-3 my-6 text-xs font-sans">
    <button type="button" class="px-3 py-2 border border-primary-light rounded-md text-primary-dark disabled:opacity-50" [disabled]="$loading() || $page() === 0"
      (click)="pageChange.emit($page() - 1)" i18n>Previous page</button>
    <span aria-live="polite" i18n>Page {{ $page() + 1 }} · {{ $total() }} items</span>
    <button type="button" class="px-3 py-2 border border-primary-light rounded-md text-primary-dark disabled:opacity-50" [disabled]="$loading() || !$hasNext()"
      (click)="pageChange.emit($page() + 1)" i18n>Next page</button>
    @if ($error()) { <p role="alert">{{ $error() }}</p>
      <button type="button" class="art-action" (click)="pageChange.emit($page())" i18n>Retry page</button> }
  </nav>`})
export class PageControlsComponent {
  readonly $page = input(0, {alias: 'page'});
  readonly $total = input(0, {alias: 'total'});
  readonly $hasNext = input(false, {alias: 'hasNext'});
  readonly $loading = input(false, {alias: 'loading'});
  readonly $error = input('', {alias: 'error'});
  readonly pageChange = output<number>();
}
