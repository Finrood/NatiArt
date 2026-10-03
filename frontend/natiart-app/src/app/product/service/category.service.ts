import {inject, Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {Category} from "../models/category.model";
import {PagedResponse} from '../../shared/models/paged-response.model';
import {environment} from "../../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class CategoryService {
  private readonly apiUrl: string = `${environment.api.product.url}${environment.api.product.endpoints.category}`;

  private readonly _http = inject(HttpClient);

  getCategoriesPage(page = 0, size = 20, admin = false): Observable<PagedResponse<Category>> {
    const params: {[key: string]: string} = {page: String(Number.isSafeInteger(page) ? Math.max(0, page) : 0),
      size: String(Number.isSafeInteger(size) ? Math.max(1, Math.min(100, size)) : 20)};
    const url: string = `${environment.api.product.url}${admin ? '/admin' : ''}/categories/page`;
    return this._http.get<PagedResponse<Category>>(url, {params});
  }

  getCategories(): Observable<Category[]> {
    return this._http.get<Category[]>(this.apiUrl);
  }

  addCategory(newCategory: Partial<Category>): Observable<Category> {
    return this._http.post<Category>(`${this.apiUrl}/create`, newCategory);
  }

  updateCategory(id: string, editCategoryData: Category): Observable<Category> {
    return this._http.put<Category>(`${this.apiUrl}/${id}`, editCategoryData);
  }

  deleteCategory(id: string): Observable<void> {
    return this._http.delete<void>(`${this.apiUrl}/${id}`);
  }

  inverseCategoryVisibility(id: string): Observable<Category> {
    return this._http.patch<Category>(`${this.apiUrl}/${id}/visibility/inverse`, null);
  }
}
