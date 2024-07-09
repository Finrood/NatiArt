import { Injectable } from '@angular/core';
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {Router} from "@angular/router";
import {Observable} from "rxjs";
import {Category} from "../models/category.model";
import {TokenService} from "./token.service";

@Injectable({
  providedIn: 'root'
})
export class CategoryService {
  private apiUrl = "http://localhost:8082/categories"
  constructor(private http: HttpClient, private tokenService: TokenService) {}

  private getHeaders(): HttpHeaders {
    const accessToken = this.tokenService.getAccessToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${accessToken}`
    });
  }

  getCategories(): Observable<any> {
    const headers = this.getHeaders();
    return this.http.get<any>(this.apiUrl, { headers });
  }

  addCategory(newCategory: Partial<Category>): Observable<any> {
    const headers = this.getHeaders();
    return this.http.post<any>(`${this.apiUrl}/create`, newCategory, { headers });
  }

  updateCategory(id: string, editCategoryData: Category): Observable<any> {
    const headers = this.getHeaders();
    return this.http.put<any>(`${this.apiUrl}/${id}`, editCategoryData, { headers });
  }

  deleteCategory(id: string): Observable<any> {
    const headers = this.getHeaders();
    return this.http.delete<any>(`${this.apiUrl}/${id}`,  { headers });
  }

  inverseCategoryVisibility(id: string): Observable<any> {
    const headers = this.getHeaders();
    return this.http.patch<any>(`${this.apiUrl}/${id}/visibility/inverse`, null, { headers });
  }
}
