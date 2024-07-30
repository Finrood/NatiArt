import { Injectable } from '@angular/core';
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {Router} from "@angular/router";
import {environment} from "../../environments/environment";
import {BehaviorSubject, Observable} from "rxjs";
import {User} from "../models/user.model";
import {TokenService} from "./token.service";

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  currentUser$ = this.currentUserSubject.asObservable();

  private readonly apiUrl: string = `${environment.directoryApiUrl}/users`;

  constructor(private http: HttpClient, private router: Router, private tokenService: TokenService) { }

  private getHeaders(): HttpHeaders {
    const accessToken = this.tokenService.getAccessToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${accessToken}`
    });
  }

  getCurrentUser(): void {
    const headers = this.getHeaders();
    this.http.get<User>(`${this.apiUrl}/current`, { headers: headers })
      .subscribe({
        next: (user) => {
          this.currentUserSubject.next(user);
        },
        error: (error) => {
          console.error('Error fetching user profile:', error);
        }
      })
  }
}
