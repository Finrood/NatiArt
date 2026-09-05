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

  getCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(this.apiUrl);
  }

  addCategory(newCategory: Partial<Category>): Observable<Category> {
    return this.http.post<Category>(`${this.apiUrl}/create`, newCategory);
  }

  updateCategory(id: string, editCategoryData: Category): Observable<Category> {
    return this.http.put<Category>(`${this.apiUrl}/${id}`, editCategoryData);
  }

  deleteCategory(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  inverseCategoryVisibility(id: string): Observable<Category> {
    return this.http.patch<Category>(`${this.apiUrl}/${id}/visibility/inverse`, null);
  }
}
