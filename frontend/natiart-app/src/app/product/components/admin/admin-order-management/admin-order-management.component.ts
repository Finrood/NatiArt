import {CurrencyPipe, DatePipe} from '@angular/common';
import {ChangeDetectorRef, Component, DestroyRef, inject, OnInit, signal} from '@angular/core';

import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {OrderDto} from '../../../models/order.model';
import {OrderService} from '../../../service/order.service';
import {OrderItemDto} from '../../../models/orderItem.model';
import {PersonalizationOption} from '../../../models/support/personalization-option';
import {Subscription} from 'rxjs';

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
  readonly $artwork = signal<{itemId: string; url: string} | null>(null);
  readonly $artworkError = signal<string | null>(null);
  readonly $loadingArtwork = signal<string | null>(null);
  private artworkSubscription: Subscription | null = null;

  constructor() {
    this._destroyRef.onDestroy((): void => this.closeArtwork());
  }

  hasArtwork(item: OrderItemDto): boolean {
    return !!item.personalization?.personalizationOptions[PersonalizationOption.CUSTOM_IMAGE];
  }

  hasGoldBorder(item: OrderItemDto): boolean {
    return item.personalization?.personalizationOptions[PersonalizationOption.GOLDEN_BORDER] === 'true';
  }

  showArtwork(orderId: string, itemId: string): void {
    this.closeArtwork();
    this.$loadingArtwork.set(itemId);
    this.artworkSubscription = this._orderService.getFulfillmentArtwork(orderId, itemId)
      .pipe(takeUntilDestroyed(this._destroyRef)).subscribe({
        next: (blob: Blob): void => {
          this.$artwork.set({itemId, url: URL.createObjectURL(blob)});
          this.$loadingArtwork.set(null);
        },
        error: (): void => {
          this.$artworkError.set(itemId);
          this.$loadingArtwork.set(null);
        },
      });
  }

  closeArtwork(): void {
    this.artworkSubscription?.unsubscribe();
    this.artworkSubscription = null;
    const preview: {itemId: string; url: string} | null = this.$artwork();
    if (preview) URL.revokeObjectURL(preview.url);
    this.$artwork.set(null);
    this.$artworkError.set(null);
    this.$loadingArtwork.set(null);
  }

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    if (this.loading && this.orders.length > 0) return;
    this.closeArtwork();
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
