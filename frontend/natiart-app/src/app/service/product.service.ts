import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {Observable} from "rxjs";
import {TokenService} from "./token.service";
import {environment} from "../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private readonly apiUrl: string = `${environment.productApiUrl}/products`;
  private readonly apiUrlImages: string = `${environment.productApiUrl}`;


  constructor(private http: HttpClient, private tokenService: TokenService) {}

  private getHeaders(): HttpHeaders {
    const accessToken = this.tokenService.getAccessToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${accessToken}`
    });
  }

  getProducts(): Observable<any> {
    const headers = this.getHeaders();
    return this.http.get<any>(this.apiUrl, { headers });
  }

  getFeaturedProducts(): Observable<any> {
    const headers = this.getHeaders();
    return this.http.get<any>(`${this.apiUrl}/featured`, { headers });
  }

  getNewProducts(): Observable<any> {
    const headers = this.getHeaders();
    return this.http.get<any>(`${this.apiUrl}/new`, { headers });
  }

  addProduct(newProduct: FormData): Observable<any> {
    const headers = this.getHeaders();
    return this.http.post<any>(`${this.apiUrl}/create`, newProduct, { headers });
  }

  updateProduct(id: string, editProductData: FormData): Observable<any> {
    const headers = this.getHeaders();
    return this.http.put<any>(`${this.apiUrl}/${id}`, editProductData, { headers });
  }

  deleteProduct(id: string): Observable<any> {
    const headers = this.getHeaders();
    return this.http.delete<any>(`${this.apiUrl}/${id}`, { headers });
  }

  inverseProductVisibility(id: string): Observable<any> {
    const headers = this.getHeaders();
    return this.http.patch<any>(`${this.apiUrl}/${id}/visibility/inverse`, null, { headers });
  }

  getImage(imagePath: string): Observable<Blob> {
    const headers = this.getHeaders();
    return this.http.get(`${this.apiUrlImages}/images?path=${encodeURIComponent(imagePath)}`, { responseType: 'blob', headers: headers });
  }
}
