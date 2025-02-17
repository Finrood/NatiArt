import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable} from 'rxjs';
import {tap} from 'rxjs/operators';
import {OrderDto} from "../models/order.model";
import {environment} from "../../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private apiUrl = `${environment.api.product.url}${environment.api.product.endpoints.order}`;
  private orderProcessingSubject = new BehaviorSubject<boolean>(false);
  orderProcessing$ = this.orderProcessingSubject.asObservable();

  constructor(private http: HttpClient) {
  }

  createOrder(order: OrderDto): Observable<OrderDto> {
    this.orderProcessingSubject.next(true);
    return this.http.post<OrderDto>(this.apiUrl, order).pipe(
      tap(() => this.orderProcessingSubject.next(false)),
    );
  }

  getOrderById(id: number): Observable<OrderDto> {
    return this.http.get<OrderDto>(`${this.apiUrl}/${id}`);
  }

  getAllOrders(): Observable<OrderDto[]> {
    return this.http.get<OrderDto[]>(this.apiUrl);
  }

  updateOrderStatus(id: number, status: string): Observable<OrderDto> {
    return this.http.put<OrderDto>(`${this.apiUrl}/${id}/status`, {status});
  }
}
