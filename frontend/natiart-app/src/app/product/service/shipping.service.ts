import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {environment} from "../../../environments/environment";

export interface ShippingEstimate {
  serviceId?: string;
  service: string;
  price: number;
  estimatedDeliveryDays: number;
}

export interface ShippingQuoteItemRequest {
  productId: string;
  quantity: number;
}

export interface ShippingQuoteRequest {
  zipCode: string;
  items: ShippingQuoteItemRequest[];
}

export interface ShippingQuoteItem {
  productId: string;
  quantity: number;
  unitPrice: number;
  lineAmount: number;
}

export interface ShippingQuote {
  quoteId: string;
  destinationPostalCode: string;
  serviceId: string;
  serviceName: string;
  expiresAt: string;
  itemAmount: number;
  shippingAmount: number;
  totalAmount: number;
  items: ShippingQuoteItem[];
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

  createQuote(request: ShippingQuoteRequest): Observable<ShippingQuote> {
    return this.http.post<ShippingQuote>(`${this.apiUrl}/quote`, request);
  }
}
