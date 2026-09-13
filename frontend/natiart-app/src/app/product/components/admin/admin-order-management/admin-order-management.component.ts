import {CurrencyPipe, DatePipe} from '@angular/common';
import {Component, OnInit} from '@angular/core';

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
    PENDING: ['PAID', 'CANCELLED'],
    PAID: ['PROCESSING', 'CANCELLED'],
    PROCESSING: ['SHIPPED', 'CANCELLED'],
    SHIPPED: ['DELIVERED']
  };

  constructor(private readonly orderService: OrderService) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.errorMessage = '';
    this.orderService.getFulfillmentOrders().subscribe({
      next: orders => {
        this.orders = orders;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Orders could not be loaded.';
        this.loading = false;
      }
    });
  }

  availableStatuses(order: OrderDto): string[] {
    return this.nextStatuses[order.status || 'PENDING'] || [];
  }

  setStatus(order: OrderDto, status: string): void {
    if (!order.id || this.savingOrderId) {
      return;
    }
    this.savingOrderId = order.id;
    this.errorMessage = '';
    this.orderService.updateOrderStatus(order.id, status).subscribe({
      next: updated => {
        const index = this.orders.findIndex(current => current.id === updated.id);
        if (index >= 0) {
          this.orders[index] = updated;
        }
        this.savingOrderId = null;
      },
      error: () => {
        this.errorMessage = 'The order status could not be updated.';
        this.savingOrderId = null;
      }
    });
  }
}
