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

  createOrder(order: OrderDto): Observable<OrderDto> {
    this.orderProcessingSubject.next(true);
    return this._http.post<OrderDto>(`${this.apiUrl}/create`, order).pipe(
      finalize(() => this.orderProcessingSubject.next(false)),
    );
  }
}
