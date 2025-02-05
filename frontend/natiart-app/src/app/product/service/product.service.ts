import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {environment} from "../../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private readonly apiUrl: string = `${environment.api.product}/products`;
  private readonly apiUrlImages: string = `${environment.api.product}`;


  constructor(private http: HttpClient) {
  }

  getProducts(): Observable<any> {
    return this.http.get<any>(this.apiUrl);
  }

  getProductsByCategory(categoryId: string): Observable<any> {
    return this.http.get<any>(this.apiUrl);
  }

  getFeaturedProducts(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/featured`);
  }

  getNewProducts(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/new`);
  }

  getProduct(productId: string | null): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${productId}`);
  }

  addProduct(newProduct: FormData): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/create`, newProduct);
  }

  updateProduct(id: string, editProductData: FormData): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/${id}`, editProductData);
  }

  deleteProduct(id: string): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/${id}`);
  }

  inverseProductVisibility(id: string): Observable<any> {
    return this.http.patch<any>(`${this.apiUrl}/${id}/visibility/inverse`, null);
  }

  getImage(imagePath: string): Observable<Blob> {
    return this.http.get(`${this.apiUrlImages}/images?path=${encodeURIComponent(imagePath)}`, {
      responseType: 'blob',
    });
  }
}
