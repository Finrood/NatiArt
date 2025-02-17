import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {Category} from "../models/category.model";
import {environment} from "../../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class CategoryService {
  private readonly apiUrl: string = `${environment.api.product.url}${environment.api.product.endpoints.category}`;

  constructor(private http: HttpClient) {
  }

  getCategories(): Observable<any> {
    return this.http.get<any>(this.apiUrl);
  }

  addCategory(newCategory: Partial<Category>): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/create`, newCategory);
  }

  updateCategory(id: string, editCategoryData: Category): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/${id}`, editCategoryData);
  }

  deleteCategory(id: string): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/${id}`);
  }

  inverseCategoryVisibility(id: string): Observable<any> {
    return this.http.patch<any>(`${this.apiUrl}/${id}/visibility/inverse`, null);
  }
}
