import {Injectable, inject} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable, Subject, throwError} from "rxjs";
import {finalize, shareReplay, tap} from "rxjs/operators";
import {environment} from "../../../environments/environment";
import {Product} from "../models/product.model";

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private readonly apiUrl: string = `${environment.api.product.url}${environment.api.product.endpoints.product}`;
  private readonly apiUrlImages: string = `${environment.api.product.url}`;
  private readonly imageRequests = new Map<string, Observable<Blob>>();


  private readonly _http: HttpClient = inject(HttpClient);
  private readonly invalidations: Subject<void> = new Subject<void>();
  readonly imageInvalidations: Observable<void> = this.invalidations.asObservable();

  invalidateImages(): void { this.imageRequests.clear(); this.invalidations.next(); }

  getProducts(): Observable<Product[]> {
    return this._http.get<Product[]>(this.apiUrl);
  }

  getProductsByCategory(categoryId: string): Observable<Product[]> {
    const params = {categoryId};
    return this._http.get<Product[]>(this.apiUrl, {params});
  }

  getFeaturedProducts(): Observable<Product[]> {
    return this._http.get<Product[]>(`${this.apiUrl}/featured`);
  }

  getNewProducts(): Observable<Product[]> {
    return this._http.get<Product[]>(`${this.apiUrl}/new`);
  }

  getProduct(productId: string | null): Observable<Product> {
    if (!productId || productId.trim().length === 0) {
      return throwError(() => new Error('Missing product id'));
    }
    return this._http.get<Product>(`${this.apiUrl}/${productId}`);
  }

  addProduct(newProduct: FormData): Observable<Product> {
    return this._http.post<Product>(`${this.apiUrl}/create`, newProduct);
  }

  updateProduct(id: string, editProductData: FormData): Observable<Product> {
    return this._http.put<Product>(`${this.apiUrl}/${id}`, editProductData).pipe(tap((): void => this.invalidateImages()));
  }

  deleteProduct(id: string): Observable<void> {
    return this._http.delete<void>(`${this.apiUrl}/${id}`).pipe(tap((): void => this.invalidateImages()));
  }

  inverseProductVisibility(id: string): Observable<Product> {
    return this._http.patch<Product>(`${this.apiUrl}/${id}/visibility/inverse`, null);
  }

  getImage(imagePath: string): Observable<Blob> {
    const cached: Observable<Blob> | undefined = this.imageRequests.get(imagePath);
    if (cached) return cached;
    const request: Observable<Blob> = this._http.get(`${this.apiUrlImages}/images`, {
      params: {path: imagePath}, responseType: 'blob'
    }).pipe(
      finalize((): void => { if (this.imageRequests.get(imagePath) === request) this.imageRequests.delete(imagePath); }),
      shareReplay({bufferSize: 1, refCount: true})
    );
    // Only concurrent requests share bytes. Completed/cancelled entries are evicted;
    // a bounded map never retains session-long image data or stale replacements.
    if (this.imageRequests.size < 32) this.imageRequests.set(imagePath, request);
    return request;
  }
}
