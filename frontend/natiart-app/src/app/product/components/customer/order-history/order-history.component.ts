import {CurrencyPipe, DatePipe} from '@angular/common';
import {ChangeDetectorRef, Component, DestroyRef, inject, OnInit} from '@angular/core';
import {RouterLink} from '@angular/router';

import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {OrderDto} from '../../../models/order.model';
import {OrderService} from '../../../service/order.service';
import {TopMenuComponent} from '../top-menu/top-menu.component';

@Component({
  selector: 'app-order-history',
  imports: [CurrencyPipe, DatePipe, RouterLink, TopMenuComponent],
  templateUrl: './order-history.component.html'
})
export class OrderHistoryComponent implements OnInit {
  readonly loadingLabel: string = $localize`Loading…`;
  readonly loadMoreLabel: string = $localize`Load more orders`;
  orders: OrderDto[] = [];
  loading = true;
  loadingMore = false;
  hasMore = false;
  errorMessage = '';
  private page = 0;
  private readonly pageSize = 20;

  private readonly _orderService = inject(OrderService);
  private readonly _destroyRef = inject(DestroyRef);
  private readonly _cdr = inject(ChangeDetectorRef);

  ngOnInit(): void {
    this._orderService.getMyOrders(this.page, this.pageSize).pipe(takeUntilDestroyed(this._destroyRef)).subscribe({
      next: orders => {
        this.orders = orders;
        this.hasMore = orders.length === this.pageSize;
        this.loading = false;
        this._cdr.markForCheck();
      },
      error: () => {
        this.errorMessage = $localize`We could not load your order history. Please try again.`;
        this.loading = false;
        this._cdr.markForCheck();
      }
    });
  }

  loadMore(): void {
    if (this.loadingMore || !this.hasMore) {
      return;
    }
    this.loadingMore = true;
    this._orderService.getMyOrders(this.page + 1, this.pageSize).pipe(takeUntilDestroyed(this._destroyRef)).subscribe({
      next: orders => {
        this.page += 1;
        this.orders = [...this.orders, ...orders];
        this.hasMore = orders.length === this.pageSize;
        this.loadingMore = false;
        this._cdr.markForCheck();
      },
      error: () => {
        this.errorMessage = $localize`We could not load more orders. Please try again.`;
        this.loadingMore = false;
        this._cdr.markForCheck();
      }
    });
  }

  statusLabel(status?: string): string {
    const labels: Record<string, string> = {PENDING: $localize`pending`, PAID: $localize`paid`,
      PROCESSING: $localize`processing`, SHIPPED: $localize`shipped`, DELIVERED: $localize`delivered`, CANCELLED: $localize`cancelled`};
    return labels[status ?? 'PENDING'] ?? labels['PENDING'];
  }
}
