import {CurrencyPipe, DatePipe} from '@angular/common';
import {ChangeDetectorRef, Component, DestroyRef, inject, OnInit, signal} from '@angular/core';

import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {OrderDto} from '../../../models/order.model';
import {OrderService} from '../../../service/order.service';

@Component({
  selector: 'app-admin-order-management',
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './admin-order-management.component.html'
})
export class AdminOrderManagementComponent implements OnInit {
  orders: OrderDto[] = [];
  loading = true;
  savingOrderId: string | null = null;
  errorMessage = '';

  readonly nextStatuses: Record<string, string[]> = {
    PAID: ['PROCESSING'],
    PROCESSING: ['SHIPPED'],
    SHIPPED: ['DELIVERED']
  };

  private readonly _cdr = inject(ChangeDetectorRef);
  private readonly _orderService = inject(OrderService);
  private readonly _destroyRef = inject(DestroyRef);
  readonly $orderErrors = signal<Record<string, string>>({});
  readonly $hasMore = signal<boolean>(true);
  private readonly pageSize: number = 20;
  private nextPage: number = 0;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    if (this.loading && this.orders.length > 0) return;
    this.orders = [];
    this.nextPage = 0;
    this.$hasMore.set(true);
    this.$orderErrors.set({});
    this.loadMore();
  }

  loadMore(): void {
    if (!this.$hasMore()) return;
    this.loading = true;
    this.errorMessage = '';
    this._orderService.getFulfillmentOrders(this.nextPage, this.pageSize)
      .pipe(takeUntilDestroyed(this._destroyRef)).subscribe({
        next: (orders: OrderDto[]): void => {
          const existing: Set<string | undefined> = new Set(this.orders.map(order => order.id));
          this.orders = [...this.orders, ...orders.filter(order => !existing.has(order.id))];
          this.nextPage += 1;
          this.$hasMore.set(orders.length === this.pageSize);
          this.loading = false;
          this._cdr.markForCheck();
        },
        error: (): void => {
          this.errorMessage = $localize`Orders could not be loaded. Please retry.`;
          this.loading = false;
          this._cdr.markForCheck();
        }
      });
  }

  statusLabel(status?: string): string {
    const labels: Record<string, string> = {PENDING: $localize`PENDING`, PAID: $localize`PAID`,
      PROCESSING: $localize`PROCESSING`, SHIPPED: $localize`SHIPPED`, DELIVERED: $localize`DELIVERED`, CANCELLED: $localize`CANCELLED`};
    return labels[status ?? 'PENDING'] ?? labels['PENDING'];
  }

  availableStatuses(order: OrderDto): string[] {
    return this.nextStatuses[order.status || 'PENDING'] || [];
  }

  setStatus(order: OrderDto, status: string): void {
    if (!order.id || this.savingOrderId || !this.availableStatuses(order).includes(status)) {
      return;
    }
    this.savingOrderId = order.id;
    this.$orderErrors.update(errors => ({...errors, [order.id!]: ''}));
    this._orderService.updateOrderStatus(order.id, status).pipe(takeUntilDestroyed(this._destroyRef)).subscribe({
      next: updated => {
        const index = this.orders.findIndex(current => current.id === updated.id);
        if (index >= 0) {
          this.orders[index] = updated;
        }
        this.savingOrderId = null;
        this._cdr.markForCheck();
      },
      error: () => {
        this.$orderErrors.update(errors => ({...errors, [order.id!]: $localize`The order status could not be updated. Please refresh or retry.`}));
        this.savingOrderId = null;
        this._cdr.markForCheck();
      }
    });
  }
}
