import { Injectable } from '@angular/core';
import {environment} from "../../environments/environment";
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {Observable} from "rxjs";
import {TokenService} from "./token.service";
import {Package} from "../models/package.model";

@Injectable({
  providedIn: 'root'
})
export class PackageService {
  private readonly apiUrl: string = `${environment.productApiUrl}/packages`

  constructor(private http: HttpClient, private tokenService: TokenService) { }

  private getHeaders(): HttpHeaders {
    const accessToken = this.tokenService.getAccessToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${accessToken}`
    });
  }

  getPackages(): Observable<any> {
    const headers = this.getHeaders();
    return this.http.get(`${this.apiUrl}`, { headers: headers })
  }

  addPackage(newPackage: Partial<Package>): Observable<any> {
    const headers = this.getHeaders();
    return this.http.post(`${this.apiUrl}/create`, newPackage, { headers: headers })
  }

  updatePackage(packageId: string, updatedPackage: Partial<Package>): Observable<any> {
    const headers = this.getHeaders();
    return this.http.put(`${this.apiUrl}/${packageId}`, updatedPackage, { headers: headers })
  }

  deletePackage(packageId: string): Observable<any> {
    const headers = this.getHeaders();
    return this.http.delete(`${this.apiUrl}/${packageId}`, { headers: headers })
  }
}