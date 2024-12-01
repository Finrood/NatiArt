import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {map, Observable} from 'rxjs';
import {environment} from "../../environments/environment";
import {PaymentCreationRequest} from "../models/paymentCreationRequest.model";
import {AuthenticationService} from "./authentication.service";

@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  private apiUrl = `${environment.productApiUrl}`;

  constructor(private http: HttpClient, private authenticationService: AuthenticationService) {
  }

  createPixPayment(paymentCreationRequest: PaymentCreationRequest): Observable<any> {
    const headers = this.getHeaders();
    return this.http.post(`${this.apiUrl}/api/payment/create`, paymentCreationRequest, {headers: headers});
  }

  getPixQrCode(paymentId: string): Observable<{
    encodedImage: string;
    payload: string;
    expirationDate: Date;
  }> {
    const headers = this.getHeaders();
    return this.http
      .get<{
        success: boolean;
        encodedImage: string;
        payload: string;
        expirationDate: [number, number, number, number, number, number];
      }>(`${this.apiUrl}/api/payment/${paymentId}/pixQrCode`, {headers})
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
    const headers = this.getHeaders();
    return this.http.get<{
      paymentId: string;
      status: string;
    }>(`${this.apiUrl}/api/payment/${paymentId}/status`, {headers: headers});
  }

  private getHeaders(): HttpHeaders {
    const accessToken = this.authenticationService.getAccessToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${accessToken}`
    });
  }
}
