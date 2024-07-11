import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Router} from "@angular/router";
import {RoleName} from "../models/user.model";
import {environment} from "../../environments/environment";
import {Observable} from "rxjs";
import {SignupRequest} from "./signup.service";
import {Credentials} from "../models/credentials.model";

@Injectable({
  providedIn: 'root'
})
export class TokenService {
  private readonly apiUrl: string = `${environment.directoryApiUrl}`;

  private accessTokenKey = 'accessToken';
  private refreshTokenKey = 'refreshToken';

  constructor(private http: HttpClient, private router: Router) {}

  getAccessToken(): string | null {
    return localStorage.getItem(this.accessTokenKey);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.refreshTokenKey);
  }

  setAccessToken(token: string): void {
    localStorage.setItem(this.accessTokenKey, token);
  }

  setRefreshToken(token: string): void {
    localStorage.setItem(this.refreshTokenKey, token);
  }

  clearTokens(): void {
    localStorage.removeItem(this.accessTokenKey);
    localStorage.removeItem(this.refreshTokenKey);
  }

  refreshToken(): void {
    const refreshToken = this.getRefreshToken();
    if (refreshToken && !this.isRefreshTokenExpired()) {
      const headers = {
        'Authorization': `Bearer ${refreshToken}`
      };

      this.http.post<{ accessToken: string, refreshToken: string }>(`${this.apiUrl}/refresh-token`, null, { headers })
        .subscribe({
          next: (response) => {
            this.setAccessToken(response.accessToken);
            this.setRefreshToken(response.refreshToken);
          },
          error: (error) => {
            console.log(error);
            this.router.navigate(['/logout'])
              .then(() => {});
          }
        });
    } else {
      this.router.navigate(['/logout'])
        .then(() => {});
    }
  }

  isAccessTokenExpired(): boolean {
    const accessToken = this.getAccessToken();
    if (!accessToken) {
      return true;
    }
    const accessTokenPayload = this.getDecodedToken(this.getAccessToken());
    const expirationTime = accessTokenPayload.exp * 1000; // Convert to milliseconds
    return expirationTime < Date.now(); // Check if token has expired
  }

  isRefreshTokenExpired(): boolean {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      return true;
    }
    const accessTokenPayload = this.getDecodedToken(this.getRefreshToken());
    const expirationTime = accessTokenPayload.exp * 1000; // Convert to milliseconds
    return expirationTime < Date.now(); // Check if token has expired
  }

  getDecodedToken(token: string | null): any {
    if (!token) {
      return null;
    }
    return JSON.parse(atob(token.split('.')[1]));
  }

  getUserRole(): string | null {
    const decodedToken = this.getDecodedToken(this.getAccessToken());
    return decodedToken ? decodedToken.roles : null;
  }

  isAdmin(): boolean {
    const role = this.getUserRole();
    return role ? role.toUpperCase() === RoleName.ADMIN : false;
  }

  login(credentials: Credentials): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/login`, credentials);
  }

  logout(): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/logout`, null);
  }
}
