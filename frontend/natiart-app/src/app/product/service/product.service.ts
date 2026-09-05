import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable, throwError} from "rxjs";
import {environment} from "../../../environments/environment";
import {Product} from "../models/product.model";

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private readonly apiUrl: string = `${environment.api.product.url}${environment.api.product.endpoints.product}`;
  private readonly apiUrlImages: string = `${environment.api.product.url}`;


  constructor(private http: HttpClient) {
  }

  getProducts(): Observable<Product[]> {
    return this.http.get<Product[]>(this.apiUrl);
  }

  getProductsByCategory(categoryId: string): Observable<Product[]> {
    const params = {categoryId};
    return this.http.get<Product[]>(this.apiUrl, {params});
  }

  getFeaturedProducts(): Observable<Product[]> {
    return this.http.get<Product[]>(`${this.apiUrl}/featured`);
  }

  getNewProducts(): Observable<Product[]> {
    return this.http.get<Product[]>(`${this.apiUrl}/new`);
  }

  getProduct(productId: string | null): Observable<Product> {
    if (!productId || productId.trim().length === 0) {
      return throwError(() => new Error('Missing product id'));
    }
    return this.http.get<Product>(`${this.apiUrl}/${productId}`);
  }

  addProduct(newProduct: FormData): Observable<Product> {
    return this.http.post<Product>(`${this.apiUrl}/create`, newProduct);
  }

  updateProduct(id: string, editProductData: FormData): Observable<Product> {
    return this.http.put<Product>(`${this.apiUrl}/${id}`, editProductData);
  }

  deleteProduct(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  inverseProductVisibility(id: string): Observable<Product> {
    return this.http.patch<Product>(`${this.apiUrl}/${id}/visibility/inverse`, null);
  }

  getImage(imagePath: string): Observable<Blob> {
    return this.http.get(`${this.apiUrlImages}/images?path=${encodeURIComponent(imagePath)}`, {
      responseType: 'blob',
    });
  }
}
