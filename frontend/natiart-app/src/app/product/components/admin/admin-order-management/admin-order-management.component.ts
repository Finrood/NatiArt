import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {OrderJourneyComponent} from '../../../../shared/components/order-journey.component';
import {CurrencyPipe, DatePipe} from '@angular/common';
import {ChangeDetectorRef, Component, DestroyRef, inject, OnInit, signal} from '@angular/core';

import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {OrderDto} from '../../../models/order.model';
import {OrderNotification, OrderService, OrderWorkspace} from '../../../service/order.service';
import {OrderItemDto} from '../../../models/orderItem.model';
import {PersonalizationOption} from '../../../models/support/personalization-option';
import {Observable, Subscription} from 'rxjs';

@Component({
  selector: 'app-admin-order-management',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, OrderJourneyComponent],
  templateUrl: './admin-order-management.component.html'
})
export class AdminOrderManagementComponent implements OnInit {
  orders: OrderDto[] = [];
  readonly $workspace = signal<OrderWorkspace | null>(null);
  readonly $workspaceError = signal<boolean>(false);
  readonly $filter = signal<string>('ALL');
  readonly $shippingOrderId = signal<string | null>(null);
  readonly $notifications = signal<OrderNotification[]>([]);
  readonly $notificationOpen = signal<boolean>(false);
  readonly $notificationError = signal<boolean>(false);
  readonly $notificationBusy = signal<string | null>(null);
  readonly $notificationRetryAccepted = signal<boolean>(false);
  readonly $notificationLoading = signal<boolean>(false);
  private readonly _forms: FormBuilder = inject(FormBuilder);
  readonly shipmentForm: FormGroup<{trackingCode: FormControl<string>; trackingUrl: FormControl<string>}> = this._forms.nonNullable.group({
    trackingCode: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9 ._-]{2,99}$/)]],
    trackingUrl: ['', [Validators.maxLength(500), Validators.pattern(/^https:\/\/[^\s]+$/)]]
  });
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
  private workspaceSubscription: Subscription | null = null;
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
    this.$shippingOrderId.set(null);
    this.loadWorkspace();
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
    const request: Observable<OrderDto[]> = this.$filter() === 'ALL'
      ? this._orderService.getFulfillmentOrders(this.nextPage, this.pageSize)
      : this._orderService.getWorkQueue(this.$filter(), this.nextPage, this.pageSize);
    request
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
    const labels: Record<string, string> = {PENDING: $localize`pending`, PAID: $localize`paid`,
      PROCESSING: $localize`processing`, SHIPPED: $localize`shipped`, DELIVERED: $localize`delivered`, CANCELLED: $localize`cancelled`};
    return labels[status ?? 'PENDING'] ?? labels['PENDING'];
  }

  transitionLabel(status: string): string {
    const labels: Record<string, string> = {PROCESSING: $localize`Mark as processing`,
      DELIVERED: $localize`Mark as delivered`};
    return labels[status] ?? '';
  }

  availableStatuses(order: OrderDto): string[] {
    return this.nextStatuses[order.status || 'PENDING'] || [];
  }

  loadWorkspace(): void {
    this.$workspaceError.set(false);
    this.workspaceSubscription?.unsubscribe();
    this.workspaceSubscription = this._orderService.getWorkspace().pipe(takeUntilDestroyed(this._destroyRef)).subscribe({
      next: (workspace: OrderWorkspace): void => this.$workspace.set(workspace),
      error: (): void => this.$workspaceError.set(true)
    });
  }

  filter(status: string): void {
    if (this.loading || this.savingOrderId || this.$filter() === status) return;
    this.$filter.set(status);
    this.reload();
  }

  startShipment(order: OrderDto): void {
    if (this.loading || this.savingOrderId || !order.id || order.status !== 'PROCESSING') return;
    this.shipmentForm.reset({trackingCode: '', trackingUrl: ''});
    this.$shippingOrderId.set(order.id);
  }

  ship(order: OrderDto): void {
    if (this.loading || !order.id || this.savingOrderId || order.status !== 'PROCESSING') return;
    this.shipmentForm.markAllAsTouched();
    if (this.shipmentForm.invalid) return;
    this.savingOrderId = order.id;
    this.$orderErrors.update(errors => ({...errors, [order.id!]: ''}));
    const shipment: {trackingCode: string; trackingUrl: string} = this.shipmentForm.getRawValue();
    this._orderService.recordShipment(order.id, shipment.trackingCode.trim(), shipment.trackingUrl.trim())
      .pipe(takeUntilDestroyed(this._destroyRef)).subscribe({
        next: (updated: OrderDto): void => {
          this.replaceOrder(updated); this.$shippingOrderId.set(null); this.savingOrderId = null;
          if (this.$filter() !== 'ALL') this.reload(); else this.loadWorkspace();
        },
        error: (): void => {
          this.savingOrderId = null;
          this.$orderErrors.update(errors => ({...errors, [order.id!]: $localize`Shipment could not be recorded. Check the carrier details and refresh if the order changed.`}));
        }
      });
  }

  private replaceOrder(updated: OrderDto): void {
    this.orders = this.orders.map(current => current.id === updated.id ? updated : current)
      .filter(current => this.$filter() === 'ALL' || current.status === this.$filter());
    this._cdr.markForCheck();
  }

  openNotifications(): void {
    if (this.$notificationLoading()) return;
    this.$notificationRetryAccepted.set(false);
    this.$notificationOpen.set(true); this.$notificationLoading.set(true); this.$notificationError.set(false);
    this._orderService.getNotificationAttention().pipe(takeUntilDestroyed(this._destroyRef)).subscribe({
      next: (jobs: OrderNotification[]): void => { this.$notifications.set(jobs); this.$notificationLoading.set(false); },
      error: (): void => { this.$notificationError.set(true); this.$notificationLoading.set(false); }
    });
  }

  retryNotification(job: OrderNotification): void {
    if (this.$notificationBusy()) return;
    this.$notificationBusy.set(job.id); this.$notificationError.set(false); this.$notificationRetryAccepted.set(false);
    this._orderService.retryNotification(job.id).pipe(takeUntilDestroyed(this._destroyRef)).subscribe({
      next: (): void => {
        this.$notificationRetryAccepted.set(true); this.$notificationBusy.set(null); this.$notifications.update(jobs => jobs.filter(current => current.id !== job.id)); this.loadWorkspace();
      },
      error: (): void => { this.$notificationBusy.set(null); this.$notificationError.set(true); }
    });
  }

  setStatus(order: OrderDto, status: string): void {
    if (this.loading || !order.id || this.savingOrderId || !this.availableStatuses(order).includes(status)) {
      return;
    }
    this.savingOrderId = order.id;
    this.$orderErrors.update(errors => ({...errors, [order.id!]: ''}));
    this._orderService.updateOrderStatus(order.id, status).pipe(takeUntilDestroyed(this._destroyRef)).subscribe({
      next: updated => {
        this.replaceOrder(updated);
        this.savingOrderId = null;
        if (this.$filter() !== 'ALL') this.reload(); else this.loadWorkspace();
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
