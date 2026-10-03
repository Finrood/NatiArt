import {inject, Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {Package} from "../models/package.model";
import {PagedResponse} from '../../shared/models/paged-response.model';
import {environment} from "../../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class PackageService {
  private readonly apiUrl: string = `${environment.api.product.url}${environment.api.product.endpoints.package}`

  private readonly _http = inject(HttpClient);

  getPackagesPage(page = 0, size = 20, admin = false): Observable<PagedResponse<Package>> {
    const params: {[key: string]: string} = {page: String(Number.isSafeInteger(page) ? Math.max(0, page) : 0),
      size: String(Number.isSafeInteger(size) ? Math.max(1, Math.min(100, size)) : 20)};
    const url: string = `${environment.api.product.url}${admin ? '/admin' : ''}/packages/page`;
    return this._http.get<PagedResponse<Package>>(url, {params});
  }

  getPackages(): Observable<Package[]> {
    return this._http.get<Package[]>(`${this.apiUrl}`)
  }

  addPackage(newPackage: Partial<Package>): Observable<Package> {
    return this._http.post<Package>(`${this.apiUrl}/create`, newPackage)
  }

  updatePackage(packageId: string, updatedPackage: Partial<Package>): Observable<Package> {
    return this._http.put<Package>(`${this.apiUrl}/${packageId}`, updatedPackage)
  }

  deletePackage(packageId: string): Observable<void> {
    return this._http.delete<void>(`${this.apiUrl}/${packageId}`)
  }
}
