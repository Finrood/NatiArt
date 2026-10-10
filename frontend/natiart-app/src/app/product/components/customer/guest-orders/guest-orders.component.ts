import {OrderJourneyComponent} from '../../../../shared/components/order-journey.component';
import {Component, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {RouterLink} from '@angular/router';
import {CurrencyPipe, DatePipe} from '@angular/common';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {GuestCheckoutService} from '../../../service/guest-checkout.service';
import {OrderDto} from '../../../models/order.model';
import {environment} from '../../../../../environments/environment';

@Component({selector: 'app-guest-orders', imports: [RouterLink, CurrencyPipe, DatePipe, OrderJourneyComponent], template: `
  <main class="art-page"><div class="art-shell max-w-3xl mx-auto py-10 px-4">
    <h1 class="art-title mb-4" i18n>Your guest orders</h1>
    <p class="mb-6" i18n>This visit is verified by email. You can view your orders without creating an account.</p>
    @if ($error()) { <p role="alert" i18n>Your order link has expired or orders could not be loaded. Request a fresh secure link.</p> }
    @if ($loading()) { <p role="status" i18n>Loading your orders...</p> }
    @for (order of $orders(); track order.id) {
      <article class="art-panel p-6 mb-4">
        <p class="text-sm text-gray-600">{{ order.orderDate | date:'mediumDate' }}</p>
        <h2 class="font-serif text-xl">#{{ order.id?.slice(0, 8) }}</h2>
        <p>{{ statusLabel(order.status) }} · {{ order.totalAmount | currency:'BRL' }}</p>
        <app-order-journey [order]="order"></app-order-journey>
        @for (item of order.items; track $index) { <p>{{ item.quantity }} × {{ item.productLabel }}</p> }
        @if (order.paymentId && order.status === 'PENDING') {
          <a class="art-link" [routerLink]="['/pix-payment', order.paymentId]" [queryParams]="{guestTracking: 1}" i18n>View PIX payment</a>
        }
      </article>
    }
    @if (!$loading() && !$error() && !$orders().length) { <p i18n>No guest orders were placed before this link was sent.</p> }
    <div class="flex flex-wrap gap-6 mt-6">
      @if (page > 0) { <button class="art-link" (click)="load(page - 1)" i18n>Previous</button> }
      @if ($orders().length === 20) { <button class="art-link" (click)="load(page + 1)" i18n>Next</button> }
      <a class="art-link" routerLink="/claim-orders" i18n>Request a new link or save orders to an account</a>
      <a class="art-link" routerLink="/dashboard" i18n>Continue exploring</a>
    </div>
  </div></main>`})
export class GuestOrdersComponent implements OnInit {
  private readonly _http: HttpClient = inject(HttpClient);
  private readonly _guest: GuestCheckoutService = inject(GuestCheckoutService);
  private readonly _destroy: DestroyRef = inject(DestroyRef);
  readonly $orders = signal<OrderDto[]>([]);
  readonly $loading = signal<boolean>(true);
  readonly $error = signal<boolean>(false);
  page: number = 0;
  statusLabel(status?: string): string {
    const labels: Record<string, string> = {PENDING: $localize`pending`, PAID: $localize`paid`,
      PROCESSING: $localize`processing`, SHIPPED: $localize`shipped`, DELIVERED: $localize`delivered`, CANCELLED: $localize`cancelled`};
    return labels[status ?? 'PENDING'] ?? labels['PENDING'];
  }
  ngOnInit(): void { this._guest.$active.set(false); this._guest.$tracking.set(true); this.load(0); }
  load(page: number): void {
    this.page = page; this.$loading.set(true); this.$error.set(false);
    this._http.get<OrderDto[]>(environment.api.product.url + '/guest/tracking/orders',
      {...this._guest.options(), params: {page}}).pipe(takeUntilDestroyed(this._destroy)).subscribe({
        next: (orders: OrderDto[]): void => { this.$orders.set(orders); this.$loading.set(false); },
        error: (): void => { this.$error.set(true); this.$loading.set(false); }
      });
  }
}
