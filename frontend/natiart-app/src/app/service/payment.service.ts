import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {Observable} from 'rxjs';
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

  getPixQrCode(paymentId: string) {
    const headers = this.getHeaders();
    return this.http.get(`${this.apiUrl}/api/payment/${paymentId}/pixQrCode`, {headers: headers});
  }

  private getHeaders(): HttpHeaders {
    const accessToken = this.authenticationService.getAccessToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${accessToken}`
    });
  }
}
