import {RouterLink} from '@angular/router';
import {DatePipe} from '@angular/common';
import {Component, computed, input} from '@angular/core';
import {OrderDto} from '../../product/models/order.model';

interface JourneyStep {label: string; date?: string; reached: boolean;}

@Component({selector: 'app-order-journey', imports: [DatePipe, RouterLink], template: `
  <section class="my-5 border-y border-primary-light/40 py-5" aria-label="Your piece's journey" i18n-aria-label>
    <h4 class="font-serif text-xl text-secondary mb-2" i18n>Your piece’s journey</h4>
    <p class="text-sm text-secondary-light mb-5">{{ $message() }}</p>
    @if (order().status === 'CANCELLED') {
      <p class="text-sm" i18n>Order cancelled</p>
      @if (order().cancelledAt) { <time class="text-xs text-secondary-light">{{ order().cancelledAt | date:'medium' }}</time> }
    } @else {
      <ol class="grid gap-4 sm:grid-cols-4">
        @for (step of $steps(); track step.label) {
          <li class="flex sm:block items-start gap-3" [attr.data-reached]="step.reached">
            <span class="flex size-6 shrink-0 items-center justify-center rounded-full border text-xs sm:mb-2"
              [class.bg-primary]="step.reached" [class.text-white]="step.reached" aria-hidden="true">{{ step.reached ? '✓' : '○' }}</span>
            <div><p class="text-sm font-medium">{{ step.label }}</p>
              @if (step.reached) { <span class="sr-only" i18n>Recorded milestone</span> }
              @if (step.date) { <time class="text-xs text-secondary-light">{{ step.date | date:'mediumDate' }}</time> }
              @else if (!step.reached) { <p class="text-xs text-secondary-light" i18n>Coming next</p> }
            </div>
          </li>
        }
      </ol>
    }
    @if (order().status === 'DELIVERED') { <a class="art-link inline-block mt-4 text-sm" routerLink="/contact" i18n>Need help with your delivery?</a> }
    @if (order().trackingCode) {
      <div class="mt-5 rounded-lg bg-background-variant p-3 text-sm break-words">
        <p><span i18n>Carrier reference:</span> <strong class="ml-1">{{ order().trackingCode }}</strong></p>
        @if ($trackingUrl()) {
          <a class="art-link inline-block mt-2" [href]="$trackingUrl()" target="_blank" rel="noopener noreferrer" i18n>Track with the carrier ↗</a>
        }
      </div>
    }
  </section>`})
export class OrderJourneyComponent {
  readonly order = input.required<OrderDto>();
  readonly $steps = computed<JourneyStep[]>((): JourneyStep[] => {
    const order: OrderDto = this.order();
    const index: number = ['PENDING', 'PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED'].indexOf(order.status ?? 'PENDING');
    return [{label: $localize`Payment confirmed`, date: order.paidAt, reached: index >= 1},
      {label: $localize`Preparing your piece`, date: order.processingAt, reached: index >= 2},
      {label: $localize`On its way`, date: order.shippedAt, reached: index >= 3},
      {label: $localize`Delivery recorded`, date: order.deliveredAt, reached: index >= 4}];
  });
  readonly $message = computed<string>((): string => {
    switch (this.order().status) {
      case 'PAID': return $localize`Your payment is confirmed. Your piece is next in the atelier’s preparation queue.`;
      case 'PROCESSING': return $localize`We’re preparing your piece and packing it with care.`;
      case 'SHIPPED': return $localize`Your parcel has been handed to the carrier. Follow its progress below.`;
      case 'DELIVERED': return $localize`The shop has recorded delivery. If your parcel has not arrived, please contact us.`;
      case 'CANCELLED': return $localize`This order will not be prepared.`;
      default: return $localize`Your order is reserved. Preparation starts after payment is confirmed.`;
    }
  });
  readonly $trackingUrl = computed<string | null>((): string | null => {
    const value: string | undefined = this.order().trackingUrl;
    if (!value) return null;
    try { const url: URL = new URL(value); return url.protocol === 'https:' && !url.username && !url.password ? url.href : null; }
    catch { return null; }
  });
}
