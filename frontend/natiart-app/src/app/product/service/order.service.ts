import {GuestCheckoutService} from './guest-checkout.service';
import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable} from 'rxjs';
import {finalize} from 'rxjs/operators';
import {OrderDto} from "../models/order.model";
import {environment} from "../../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private apiUrl: string = `${environment.api.product.url}${environment.api.product.endpoints.order}`;
  private orderProcessingSubject: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);
  orderProcessing$: Observable<boolean> = this.orderProcessingSubject.asObservable();

  private readonly _guest: GuestCheckoutService = inject(GuestCheckoutService);
  private readonly _http: HttpClient = inject(HttpClient);

  createOrder(order: OrderDto, idempotencyKey: string = crypto.randomUUID()): Observable<OrderDto> {
    this.orderProcessingSubject.next(true);
    return this._http.post<OrderDto>(`${this._guest.$active() ? environment.api.product.url + "/guest/orders" : this.apiUrl}/create`, order, {
      ...(this._guest.$active() ? this._guest.options() : {}),
      headers: {...(this._guest.$active() ? this._guest.options().headers : {}), 'Idempotency-Key': idempotencyKey},
    }).pipe(
      finalize(() => this.orderProcessingSubject.next(false)),
    );
  }

  getMyOrders(page = 0, size = 20): Observable<OrderDto[]> {
    return this._http.get<OrderDto[]>(environment.api.product.url + "/account/orders", {params: {page, size}});
  }

  getMyOrder(orderId: string): Observable<OrderDto> {
    return this._http.get<OrderDto>(`${environment.api.product.url}/account/orders/${encodeURIComponent(orderId)}`);
  }

  getFulfillmentOrders(page = 0, size = 20): Observable<OrderDto[]> {
    return this._http.get<OrderDto[]>(`${environment.api.product.url}/admin/orders`, {params: {page, size}});
  }

  updateOrderStatus(orderId: string, status: string): Observable<OrderDto> {
    return this._http.patch<OrderDto>(
      `${environment.api.product.url}/admin/orders/${encodeURIComponent(orderId)}/status`,
      {status},
    );
  }

  getWorkspace(): Observable<OrderWorkspace> {
    return this._http.get<OrderWorkspace>(`${environment.api.product.url}/admin/order-workspace`);
  }

  getWorkQueue(status: string, page: number = 0, size: number = 20): Observable<OrderDto[]> {
    return this._http.get<OrderDto[]>(`${environment.api.product.url}/admin/order-workspace/queue`, {params: {status, page, size}});
  }

  recordShipment(orderId: string, trackingCode: string, trackingUrl: string): Observable<OrderDto> {
    return this._http.post<OrderDto>(`${environment.api.product.url}/admin/orders/${encodeURIComponent(orderId)}/shipment`,
      {trackingCode, trackingUrl: trackingUrl || null});
  }

  getNotificationAttention(): Observable<OrderNotification[]> {
    return this._http.get<OrderNotification[]>(`${environment.api.product.url}/admin/order-notifications/attention`);
  }

  retryNotification(id: string): Observable<void> {
    return this._http.post<void>(`${environment.api.product.url}/admin/order-notifications/${encodeURIComponent(id)}/retry`, {});
  }

  getFulfillmentArtwork(orderId: string, itemId: string): Observable<Blob> {
    return this._http.get(
      `${environment.api.product.url}/admin/orders/${encodeURIComponent(orderId)}/items/${encodeURIComponent(itemId)}/artwork`,
      {responseType: 'blob'},
    );
  }
}

export interface OrderWorkspace {
  awaitingPayment: number; readyToPrepare: number; preparing: number; inTransit: number; failedNotifications: number;
}
export interface OrderNotification {
  id: string; orderId: string; milestone: string; attempts: number; exhausted: boolean; nextAttemptAt: string; retryRequestedAt?: string;
}
