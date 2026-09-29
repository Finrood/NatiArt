import {inject, Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable, throwError} from "rxjs";
import {PagedResponse} from '../../shared/models/paged-response.model';
import {environment} from "../../../environments/environment";
import {Product} from "../models/product.model";

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private readonly apiUrl: string = `${environment.api.product.url}${environment.api.product.endpoints.product}`;
  private readonly apiUrlImages: string = `${environment.api.product.url}`;


  private readonly _http = inject(HttpClient);

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
    return this._http.put<Product>(`${this.apiUrl}/${id}`, editProductData);
  }

  deleteProduct(id: string): Observable<void> {
    return this._http.delete<void>(`${this.apiUrl}/${id}`);
  }

  inverseProductVisibility(id: string): Observable<Product> {
    return this._http.patch<Product>(`${this.apiUrl}/${id}/visibility/inverse`, null);
  }

  imageUrl(imagePath: string): string {
    return `${this.apiUrlImages}/images?path=${encodeURIComponent(imagePath)}`;
  }

  getImage(imagePath: string): Observable<Blob> {
    return this._http.get(`${this.apiUrlImages}/images?path=${encodeURIComponent(imagePath)}`, {
      responseType: 'blob',
    });
  }
}
