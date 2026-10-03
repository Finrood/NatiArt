import {inject, Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable, Subject, throwError} from "rxjs";
import {finalize, shareReplay, tap} from "rxjs/operators";
import {PagedResponse} from '../../shared/models/paged-response.model';
import {environment} from "../../../environments/environment";
import {Product} from "../models/product.model";

export interface CustomerUploadResponse {
  uploadId: string;
}

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private readonly apiUrl: string = `${environment.api.product.url}${environment.api.product.endpoints.product}`;
  private readonly apiUrlImages: string = `${environment.api.product.url}`;


  private readonly _http = inject(HttpClient);
  private readonly imageRequests = new Map<string, Observable<Blob>>();
  private readonly invalidations: Subject<void> = new Subject<void>();
  readonly imageInvalidations: Observable<void> = this.invalidations.asObservable();

  invalidateImages(): void { this.imageRequests.clear(); this.invalidations.next(); }

  getProductsPage(categoryId?: string, page = 0, size = 20, query = '', admin = false): Observable<PagedResponse<Product>> {
    const params: {[key: string]: string} = {page: String(Number.isSafeInteger(page) ? Math.max(0, page) : 0),
      size: String(Number.isSafeInteger(size) ? Math.max(1, Math.min(100, size)) : 20)};
    if (categoryId?.trim()) params['categoryId'] = categoryId.trim();
    if (query.trim()) params['query'] = query.trim();
    const url: string = `${environment.api.product.url}${admin ? '/admin' : ''}/products/page`;
    return this._http.get<PagedResponse<Product>>(url, {params});
  }

  getProducts(categoryId?: string, page = 0, size = 20): Observable<Product[]> {
    const params: { [key: string]: string } = {
      page: String(Math.max(0, page)),
      size: String(Math.max(1, Math.min(100, size))),
    };
    if (categoryId?.trim()) {
      params['categoryId'] = categoryId.trim();
    }
    return this._http.get<Product[]>(this.apiUrl, {params});
  }

  getProductsByCategory(categoryId: string, page = 0, size = 20): Observable<Product[]> {
    const params = {
      categoryId,
      page: String(Math.max(0, page)),
      size: String(Math.max(1, Math.min(100, size))),
    };
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

  imageUrl(imagePath: string): string {
    return `${this.apiUrlImages}/images?path=${encodeURIComponent(imagePath)}`;
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

  uploadCustomerImage(file: File): Observable<CustomerUploadResponse> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this._http.post<CustomerUploadResponse>(`${this.apiUrlImages}/customer/uploads`, form);
  }
}
