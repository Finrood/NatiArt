import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {map, Observable} from 'rxjs';
import {PaymentCreationRequest} from "../models/paymentCreationRequest.model";
import {PaymentCreationResponse} from "../models/paymentCreationResonse.model";
import {environment} from "../../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  private apiUrl = `${environment.api.product.url}`;

  constructor(private http: HttpClient) {
  }

  createPixPayment(paymentCreationRequest: PaymentCreationRequest, idempotencyKey?: string): Observable<PaymentCreationResponse> {
    const options = idempotencyKey ? {headers: {'Idempotency-Key': idempotencyKey}} : {};
    return this.http.post<PaymentCreationResponse>(`${this.apiUrl}/payments/create`, paymentCreationRequest, options);
  }

  getPixQrCode(paymentId: string): Observable<{
    encodedImage: string;
    payload: string;
    expirationDate: Date;
  }> {
    return this.http
      .get<{
        success: boolean;
        encodedImage: string;
        payload: string;
        expirationDate: string | [number, number, number, number, number, number];
      }>(`${this.apiUrl}/payments/${paymentId}/pix-qr-code`)
      .pipe(
        map((response) => ({
          encodedImage: `data:image/png;base64,${response.encodedImage}`,
          payload: response.payload,
          expirationDate: parseExpirationDate(response.expirationDate),
        }))
      );
  }

  getPaymentStatus(paymentId: string): Observable<{ paymentId: string; status: string }> {
    return this.http.get<{
      paymentId: string;
      status: string;
    }>(`${this.apiUrl}/payments/${paymentId}/status`);
  }
}

function parseExpirationDate(value: string | [number, number, number, number, number, number]): Date {
  if (Array.isArray(value)) {
    return new Date(value[0], value[1] - 1, value[2], value[3], value[4], value[5]);
  }
  return new Date(value);
}
