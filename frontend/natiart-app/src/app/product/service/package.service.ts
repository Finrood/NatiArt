import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {Package} from "../models/package.model";
import {environment} from "../../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class PackageService {
  private readonly apiUrl: string = `${environment.api.product.url}${environment.api.product.endpoints.package}`

  constructor(private http: HttpClient) {
  }

  getPackages(): Observable<any> {
    return this.http.get(`${this.apiUrl}`)
  }

  addPackage(newPackage: Partial<Package>): Observable<any> {
    return this.http.post(`${this.apiUrl}/create`, newPackage)
  }

  updatePackage(packageId: string, updatedPackage: Partial<Package>): Observable<any> {
    return this.http.put(`${this.apiUrl}/${packageId}`, updatedPackage)
  }

  deletePackage(packageId: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${packageId}`)
  }
}
