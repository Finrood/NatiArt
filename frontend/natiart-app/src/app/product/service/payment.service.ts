import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {map, Observable} from 'rxjs';
import {PaymentCreationRequest} from "../models/paymentCreationRequest.model";
import {environment} from "../../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  private apiUrl = `${environment.api.product}`;

  constructor(private http: HttpClient) {
  }

  createPixPayment(paymentCreationRequest: PaymentCreationRequest): Observable<any> {
    return this.http.post(`${this.apiUrl}/api/payment/create`, paymentCreationRequest);
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
        expirationDate: [number, number, number, number, number, number];
      }>(`${this.apiUrl}/api/payment/${paymentId}/pixQrCode`)
      .pipe(
        map((response) => ({
          encodedImage: `data:image/png;base64,${response.encodedImage}`,
          payload: response.payload,
          expirationDate: new Date(
            response.expirationDate[0], // Year
            response.expirationDate[1] - 1, // Month (0-indexed)
            response.expirationDate[2], // Day
            response.expirationDate[3], // Hour
            response.expirationDate[4], // Minute
            response.expirationDate[5] // Second
          ),
        }))
      );
  }

  getPaymentStatus(paymentId: string): Observable<any> {
    return this.http.get<{
      paymentId: string;
      status: string;
    }>(`${this.apiUrl}/api/payment/${paymentId}/status`);
  }
}
