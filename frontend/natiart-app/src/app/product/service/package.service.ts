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

  getPackages(): Observable<Package[]> {
    return this.http.get<Package[]>(`${this.apiUrl}`)
  }

  addPackage(newPackage: Partial<Package>): Observable<Package> {
    return this.http.post<Package>(`${this.apiUrl}/create`, newPackage)
  }

  updatePackage(packageId: string, updatedPackage: Partial<Package>): Observable<Package> {
    return this.http.put<Package>(`${this.apiUrl}/${packageId}`, updatedPackage)
  }

  deletePackage(packageId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${packageId}`)
  }
}
