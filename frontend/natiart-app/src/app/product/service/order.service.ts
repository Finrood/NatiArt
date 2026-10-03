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

  private readonly _http: HttpClient = inject(HttpClient);

  createOrder(order: OrderDto, idempotencyKey: string = crypto.randomUUID()): Observable<OrderDto> {
    this.orderProcessingSubject.next(true);
    return this._http.post<OrderDto>(`${this.apiUrl}/create`, order, {
      headers: {'Idempotency-Key': idempotencyKey},
    }).pipe(
      finalize(() => this.orderProcessingSubject.next(false)),
    );
  }

  getMyOrders(page = 0, size = 20): Observable<OrderDto[]> {
    return this._http.get<OrderDto[]>(this.apiUrl, {params: {page, size}});
  }

  getMyOrder(orderId: string): Observable<OrderDto> {
    return this._http.get<OrderDto>(`${this.apiUrl}/${encodeURIComponent(orderId)}`);
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
}
