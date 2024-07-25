import { Injectable } from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {environment} from "../../environments/environment";

export interface ShippingEstimate {
  service: string;
  price: number;
  estimatedDeliveryDays: number;
}

@Injectable({
  providedIn: 'root'
})
export class ShippingService {
  private readonly apiUrl: string = `${environment.productApiUrl}/shipping`;

  constructor(private http: HttpClient) {}

  calculateShipping(request: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/estimate`, request);
  }
}
