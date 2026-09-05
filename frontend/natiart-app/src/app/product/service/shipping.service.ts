import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {environment} from "../../../environments/environment";

export interface ShippingEstimate {
  service: string;
  price: number;
  estimatedDeliveryDays: number;
}

export interface ShippingEstimateRequest {
  to: string;
  weight: number;
  length: number;
  width: number;
  height: number;
  quantity: number;
}

@Injectable({
  providedIn: 'root'
})
export class ShippingService {
  private readonly apiUrl: string = `${environment.api.product.url}/shipping`;

  constructor(private http: HttpClient) {
  }

  calculateShipping(request: ShippingEstimateRequest): Observable<ShippingEstimate[]> {
    return this.http.post<ShippingEstimate[]>(`${this.apiUrl}/estimate`, request);
  }
}
