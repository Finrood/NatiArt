import {inject, Injectable} from '@angular/core';
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

  private readonly _http = inject(HttpClient);

  createPixPayment(
    paymentCreationRequest: PaymentCreationRequest,
    idempotencyKey: string = crypto.randomUUID(),
  ): Observable<PaymentCreationResponse> {
    return this._http.post<PaymentCreationResponse>(`${this.apiUrl}/payments/create`, paymentCreationRequest, {
      headers: {'Idempotency-Key': idempotencyKey},
    });
  }

  getPixQrCode(paymentId: string): Observable<{
    encodedImage: string;
    payload: string;
    expirationDate: Date;
  }> {
    return this._http
      .get<{
        success: boolean;
        encodedImage: string;
        payload: string;
        expirationDate: string | [number, number, number, number, number, number];
      }>(`${this.apiUrl}/payments/${paymentId}/pix-qr-code`)
      .pipe(
        map((response) => {
          if (response?.success !== true || typeof response.encodedImage !== 'string'
            || !response.encodedImage.trim() || typeof response.payload !== 'string' || !response.payload.trim()
            || !(typeof response.expirationDate === 'string' || Array.isArray(response.expirationDate))) {
            throw new Error('Invalid PIX QR response');
          }
          const expirationDate: Date = parseExpirationDate(response.expirationDate);
          if (!Number.isFinite(expirationDate.getTime())) {
            throw new Error('Invalid PIX expiration date');
          }
          return {encodedImage: `data:image/png;base64,${response.encodedImage}`,
            payload: response.payload, expirationDate};
        })
      );
  }

  getPaymentStatus(paymentId: string): Observable<{ paymentId: string; status: string; orderId?: string }> {
    return this._http.get<{
      paymentId: string;
      status: string;
      orderId?: string;
    }>(`${this.apiUrl}/payments/${paymentId}/status`);
  }
}

function parseExpirationDate(value: string | number[]): Date {
  if (Array.isArray(value)) {
    if ((value.length !== 6 && value.length !== 7) || !value.every(Number.isSafeInteger)) return new Date(NaN);
    const parsed: Date = new Date(value[0], value[1] - 1, value[2], value[3], value[4], value[5]);
    if (parsed.getFullYear() !== value[0] || parsed.getMonth() !== value[1] - 1 || parsed.getDate() !== value[2]
      || parsed.getHours() !== value[3] || parsed.getMinutes() !== value[4] || parsed.getSeconds() !== value[5]) {
      return new Date(NaN);
    }
    return parsed;
  }
  return new Date(value);
}
