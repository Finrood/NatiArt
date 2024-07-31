import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from "@angular/common/http";
import {Router} from "@angular/router";
import {RoleName, User} from "../models/user.model";
import {environment} from "../../environments/environment";
import {BehaviorSubject, map, Observable} from "rxjs";
import {SignupRequest} from "./signup.service";
import {Credentials} from "../models/credentials.model";

@Injectable({
  providedIn: 'root'
})
export class AuthenticationService {
  private readonly apiUrl: string = `${environment.directoryApiUrl}`;

  private tokenExpiryCheckIntervalId: any;

  private isLoggedInSubject = new BehaviorSubject<boolean>(false);
  isLoggedIn$ = this.isLoggedInSubject.asObservable();
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  currentUser$ = this.currentUserSubject.asObservable();

  private accessTokenKey = 'accessToken';
  private refreshTokenKey = 'refreshToken';

  constructor(private http: HttpClient, private router: Router) {
    this.isLoggedInSubject.next(this.getAccessToken() !== null);
    this.startTokenExpiryCheck();
  }

  private getHeaders(): HttpHeaders {
    const accessToken = this.getAccessToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${accessToken}`
    });
  }

  getCurrentUser(): void {
    const headers = this.getHeaders();
    this.http.get<User>(`${this.apiUrl}/users/current`, { headers: headers })
      .subscribe({
        next: (user) => {
          this.isLoggedInSubject.next(true);
          this.currentUserSubject.next(user);
        },
        error: (error) => {
          console.error('Error fetching user profile:', error);
        }
      })
  }

  getAccessToken(): string | null | undefined {
    const token = localStorage.getItem(this.accessTokenKey);
    if (token === null || token === undefined) {
      return null;
    }
    return token;
  }

  getRefreshToken(): string | null | undefined {
    return localStorage.getItem(this.refreshTokenKey);
  }

  setAccessToken(token: string | null | undefined): void {
    if (token === null || token === undefined) {
      localStorage.removeItem(this.accessTokenKey);
    } else {
      localStorage.setItem(this.accessTokenKey, token);
    }
  }

  setRefreshToken(token: string | null | undefined): void {
    if (token === null || token === undefined) {
      localStorage.removeItem(this.refreshTokenKey);
    } else {
      localStorage.setItem(this.refreshTokenKey, token);
    }
  }

  clearTokens(): void {
    this.currentUserSubject.next(null);
    localStorage.removeItem(this.accessTokenKey);
    localStorage.removeItem(this.refreshTokenKey);
  }

  refreshToken(): void {
    const refreshToken = this.getRefreshToken();
    if (refreshToken && !this.isRefreshTokenExpired()) {
      const headers = {
        'Authorization': `Bearer ${refreshToken}`
      };

      this.http.post<{ accessToken: string, refreshToken: string }>(`${this.apiUrl}/refresh-token`, null, { headers: headers })
        .subscribe({
          next: (response) => {
            this.setAccessToken(response.accessToken);
            this.setRefreshToken(response.refreshToken);
            this.getCurrentUser();
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
    if(expirationTime < Date.now()) {
      return true;
    } else {
      this.isLoggedInSubject.next(true);
      return false;
    }
  }

  isRefreshTokenExpired(): boolean {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      return true;
    }
    const accessTokenPayload = this.getDecodedToken(this.getRefreshToken());
    const expirationTime = accessTokenPayload.exp * 1000; // Convert to milliseconds
    return expirationTime < Date.now();
  }

  getDecodedToken(token: string | null | undefined): any {
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
    return this.http.post<Credentials>(`${this.apiUrl}/login`, credentials).pipe(
      map((response) => {
        this.isLoggedInSubject.next(true);
        return response;
      })
    );
  }

  logout(): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/logout`, null).pipe(
      map((response) => {
        this.clearTokens();
        this.isLoggedInSubject.next(false);
        return response;
      })
    );
  }

  public startTokenExpiryCheck(): void {
    if (!this.tokenExpiryCheckIntervalId) {
      this.tokenExpiryCheckIntervalId = setInterval(() => {
        if (this.getAccessToken() && this.isAccessTokenExpired()) {
          console.log("Tokens have expired. Trying to renew them");
          this.refreshToken();
        }
      }, 10000); // Every second
    }
  }
}
