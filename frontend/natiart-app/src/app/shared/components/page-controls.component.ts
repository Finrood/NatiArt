import {Component, input, output} from '@angular/core';

@Component({selector: 'app-page-controls', template: `
  <nav aria-label="Pagination" class="flex flex-wrap items-center gap-4 my-6">
    <button type="button" class="px-4 py-2 border rounded" [disabled]="$loading() || $page() === 0"
      (click)="pageChange.emit($page() - 1)">Previous page</button>
    <span aria-live="polite">Page {{ $page() + 1 }} · {{ $total() }} items</span>
    <button type="button" class="px-4 py-2 border rounded" [disabled]="$loading() || !$hasNext()"
      (click)="pageChange.emit($page() + 1)">Next page</button>
    @if ($error()) { <p role="alert">{{ $error() }}</p>
      <button type="button" (click)="pageChange.emit($page())">Retry page</button> }
  </nav>`})
export class PageControlsComponent {
  readonly $page = input(0, {alias: 'page'});
  readonly $total = input(0, {alias: 'total'});
  readonly $hasNext = input(false, {alias: 'hasNext'});
  readonly $loading = input(false, {alias: 'loading'});
  readonly $error = input('', {alias: 'error'});
  readonly pageChange = output<number>();
}
