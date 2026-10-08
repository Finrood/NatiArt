import {inject, Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {environment} from "../../../environments/environment";
import {PersonalizationDto} from '../models/orderItem.model';

export interface ShippingEstimate {
  serviceId?: string;
  service: string;
  price: number;
  estimatedDeliveryDays: number;
}

export interface ShippingQuoteItemRequest {
  productId: string;
  quantity: number;
  personalization?: PersonalizationDto;
}

export interface ShippingQuoteRequest {
  zipCode: string;
  items: ShippingQuoteItemRequest[];
}

export interface ShippingQuoteItem {
  productId: string;
  personalizationKey?: string;
  quantity: number;
  unitPrice: number;
  lineAmount: number;
}

export interface ShippingQuote {
  quoteId: string;
  destinationPostalCode: string;
  serviceId: string;
  serviceName: string;
  estimatedDeliveryDays?: number | null;
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

export interface ShippingBasketEstimateRequest {
  zipCode: string;
  items: {productId: string; quantity: number; personalized: boolean}[];
}

@Injectable({
  providedIn: 'root'
})
export class ShippingService {
  private readonly apiUrl: string = `${environment.api.product.url}/shipping`;

  private readonly _http: HttpClient = inject(HttpClient);

  calculateShipping(request: ShippingEstimateRequest): Observable<ShippingEstimate[]> {
    return this._http.post<ShippingEstimate[]>(`${this.apiUrl}/estimate`, request);
  }

  createQuote(request: ShippingQuoteRequest): Observable<ShippingQuote> {
    return this._http.post<ShippingQuote>(`${this.apiUrl}/quote`, request);
  }

  estimateBasket(request: ShippingBasketEstimateRequest): Observable<ShippingEstimate[]> {
    return this._http.post<ShippingEstimate[]>(`${this.apiUrl}/basket-estimate`, request);
  }
}
