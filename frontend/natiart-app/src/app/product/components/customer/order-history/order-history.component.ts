import {CurrencyPipe, DatePipe} from '@angular/common';
import {Component, OnInit} from '@angular/core';
import {RouterLink} from '@angular/router';

import {OrderDto} from '../../../models/order.model';
import {OrderService} from '../../../service/order.service';
import {TopMenuComponent} from '../top-menu/top-menu.component';

@Component({
  selector: 'app-order-history',
  imports: [CurrencyPipe, DatePipe, RouterLink, TopMenuComponent],
  templateUrl: './order-history.component.html'
})
export class OrderHistoryComponent implements OnInit {
  orders: OrderDto[] = [];
  loading = true;
  loadingMore = false;
  hasMore = false;
  errorMessage = '';
  private page = 0;
  private readonly pageSize = 20;

  constructor(private readonly orderService: OrderService) {}

  ngOnInit(): void {
    this.orderService.getMyOrders(this.page, this.pageSize).subscribe({
      next: orders => {
        this.orders = orders;
        this.hasMore = orders.length === this.pageSize;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'We could not load your order history. Please try again.';
        this.loading = false;
      }
    });
  }

  loadMore(): void {
    if (this.loadingMore || !this.hasMore) {
      return;
    }
    this.loadingMore = true;
    this.orderService.getMyOrders(this.page + 1, this.pageSize).subscribe({
      next: orders => {
        this.page += 1;
        this.orders = [...this.orders, ...orders];
        this.hasMore = orders.length === this.pageSize;
        this.loadingMore = false;
      },
      error: () => {
        this.errorMessage = 'We could not load more orders. Please try again.';
        this.loadingMore = false;
      }
    });
  }

  statusLabel(status?: string): string {
    return status ? status.toLowerCase().replace('_', ' ') : 'pending';
  }
}
