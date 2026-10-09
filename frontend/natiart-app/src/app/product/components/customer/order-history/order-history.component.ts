import {CurrencyPipe, DatePipe} from '@angular/common';
import {ChangeDetectorRef, Component, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute, ParamMap, RouterLink} from '@angular/router';

import {catchError, map, merge, Observable, of, Subject, Subscription, switchMap, tap} from 'rxjs';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {OrderDto} from '../../../models/order.model';
import {OrderService} from '../../../service/order.service';
import {PersonalizationOption} from '../../../models/support/personalization-option';

@Component({
  selector: 'app-order-history',
  imports: [CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './order-history.component.html'
})
export class OrderHistoryComponent implements OnInit {
  protected readonly PersonalizationOption: typeof PersonalizationOption = PersonalizationOption;
  readonly loadingLabel: string = $localize`Loading…`;
  readonly loadMoreLabel: string = $localize`Load more orders`;
  readonly $selectedOrderId = signal<string | null>(null);
  private readonly _route = inject(ActivatedRoute);
  orders: OrderDto[] = [];
  loading = true;
  loadingMore = false;
  hasMore = false;
  errorMessage = '';
  private page = 0;
  private loadMoreSubscription: Subscription | null = null;
  private readonly pageSize = 20;

  private readonly _orderService = inject(OrderService);
  private readonly _destroyRef = inject(DestroyRef);
  private readonly _cdr = inject(ChangeDetectorRef);

  private readonly _retry: Subject<void> = new Subject<void>();

  retryOrders(): void {
    if (this.orders.length > 0 && this.hasMore) this.loadMore();
    else this._retry.next();
  }

  ngOnInit(): void {
    this._route.queryParamMap.pipe(
      switchMap((params: ParamMap): Observable<ParamMap> => merge(of(params), this._retry.pipe(map((): ParamMap => params)))),
      tap((params: ParamMap): void => {
        this.loadMoreSubscription?.unsubscribe();
        this.loadingMore = false;
        this.$selectedOrderId.set(params.get('orderId'));
        this.page = 0;
        this.orders = [];
        this.loading = true;
        this.errorMessage = '';
        this.hasMore = false;
      }),
      switchMap(() => (this.$selectedOrderId()
        ? this._orderService.getMyOrder(this.$selectedOrderId()!).pipe(map((order: OrderDto): OrderDto[] => [order]))
        : this._orderService.getMyOrders(0, this.pageSize)).pipe(
          catchError(() => {
            this.errorMessage = $localize`We could not load your order history. Please try again.`;
            return of<OrderDto[]>([]);
          })
        )),
      takeUntilDestroyed(this._destroyRef)
    ).subscribe({
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
    if (this.$selectedOrderId() || this.loadingMore || !this.hasMore) {
      return;
    }
    this.loadingMore = true;
    this.errorMessage = '';
    this.loadMoreSubscription = this._orderService.getMyOrders(this.page + 1, this.pageSize).pipe(takeUntilDestroyed(this._destroyRef)).subscribe({
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
