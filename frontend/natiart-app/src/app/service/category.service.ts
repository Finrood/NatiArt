import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {Observable} from "rxjs";
import {Category} from "../models/category.model";
import {AuthenticationService} from "./authentication.service";
import {environment} from "../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class CategoryService {
  private readonly apiUrl: string = `${environment.productApiUrl}/categories`;

  constructor(private http: HttpClient, private authenticationService: AuthenticationService) {
  }

  getCategories(): Observable<any> {
    const headers = this.getHeaders();
    return this.http.get<any>(this.apiUrl, {headers});
  }

  addCategory(newCategory: Partial<Category>): Observable<any> {
    const headers = this.getHeaders();
    return this.http.post<any>(`${this.apiUrl}/create`, newCategory, {headers});
  }

  updateCategory(id: string, editCategoryData: Category): Observable<any> {
    const headers = this.getHeaders();
    return this.http.put<any>(`${this.apiUrl}/${id}`, editCategoryData, {headers});
  }

  deleteCategory(id: string): Observable<any> {
    const headers = this.getHeaders();
    return this.http.delete<any>(`${this.apiUrl}/${id}`, {headers});
  }

  inverseCategoryVisibility(id: string): Observable<any> {
    const headers = this.getHeaders();
    return this.http.patch<any>(`${this.apiUrl}/${id}/visibility/inverse`, null, {headers});
  }

  private getHeaders(): HttpHeaders {
    const accessToken = this.authenticationService.getAccessToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${accessToken}`
    });
  }
}
