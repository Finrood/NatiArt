import { Injectable } from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Router} from "@angular/router";
import {Observable} from "rxjs";
import {TokenService} from "./token.service";

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private apiUrl = "http://localhost:8082/products"
  constructor(private http: HttpClient, private tokenService: TokenService) {}

  getProducts(): Observable<any> {
    const accessToken = this.tokenService.getAccessToken();
    const headers = {
      'Authorization': `Bearer ${accessToken}`
    };

    return this.http.get<any>(this.apiUrl, { headers} );
  }
}
