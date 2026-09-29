import {DestroyRef, signal} from '@angular/core';
import {Observable, Subscription} from 'rxjs';
import {PagedResponse} from '../models/paged-response.model';

export class PagedList<T> {
  readonly $page = signal(0);
  readonly $total = signal(0);
  readonly $hasNext = signal(false);
  readonly $loading = signal(false);
  readonly $error = signal('');
  private request: Subscription | null = null;

  constructor(private readonly loadPage: (page: number) => Observable<PagedResponse<T>>,
              private readonly publish: (items: T[]) => void, destroyed: DestroyRef, private readonly onError: () => void = (): void => {}) {
    destroyed.onDestroy((): void => this.request?.unsubscribe());
  }

  load(page: number): void {
    this.request?.unsubscribe();
    this.$page.set(Math.max(0, Math.floor(page)));
    this.$loading.set(true);
    this.$error.set('');
    this.request = this.loadPage(this.$page()).subscribe({
      next: (response: PagedResponse<T>): void => {
        if (response.items.length === 0 && response.page > 0 && response.total <= response.page * response.size) {
          this.load(Math.max(0, Math.ceil(response.total / response.size) - 1));
          return;
        }
        this.$page.set(response.page);
        this.$total.set(response.total);
        this.$hasNext.set(response.hasNext);
        this.publish(response.items);
        this.$loading.set(false);
      },
      error: (): void => {
        this.$error.set('Could not load this page. Please retry.');
        this.onError();
        this.$loading.set(false);
      },
    });
  }
}
