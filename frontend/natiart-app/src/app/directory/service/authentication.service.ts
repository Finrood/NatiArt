import {Injectable} from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpHeaders} from "@angular/common/http";
import {Router} from "@angular/router";
import {RoleName, User} from "../models/user.model";
import {environment} from "../../../environments/environment";
import {BehaviorSubject, catchError, Observable, throwError, timer} from "rxjs";
import {Credentials} from "../models/credentials.model";
import {map, switchMap, tap} from "rxjs/operators";
import {LoginResponse} from "../models/loginResponse.model";
import {TokenService} from "./token.service";

interface AuthState {
  isLoggedIn: boolean;
  user: User | null;
}

@Injectable({
  providedIn: 'root'
})
export class AuthenticationService {
  private readonly apiUrl: string = `${environment.api.directory.url}`;
  private readonly tokenCheckInterval = 60000; // 1 minute
  private readonly tokenRefreshBuffer = 300000; // 5 minutes before expiration

  stateSubject: BehaviorSubject<AuthState> = new BehaviorSubject<AuthState>({
    isLoggedIn: false,
    user: null
  });

  constructor(private http: HttpClient, private router: Router, private tokenService: TokenService) {
    this.initializeAuthState();
    this.startTokenMonitoring();
  }

  login(credentials: Credentials): Observable<User> {
    return this.http.post<LoginResponse>(
      `${this.apiUrl}${environment.api.directory.endpoints.login}`,
      credentials
    ).pipe(
      tap((loginResponse: LoginResponse) => {
        this.tokenService.accessToken = loginResponse.accessToken;
        this.tokenService.refreshToken = loginResponse.refreshToken;
      }),
      switchMap(() => this.fetchCurrentUser()),
      catchError(error => this.handleError(error, 'Login failed'))
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}${environment.api.directory.endpoints.logout}`, null).pipe(
      tap(() => this.resetAuthState()),
      catchError(error => this.handleError(error, 'Logout failed'))
    );
  }

  get isAdmin(): boolean {
    return this.stateSubject.value.user?.role === RoleName.ADMIN;
  }

  fetchCurrentUser(): Observable<User> {
    return this.http.get<User>(
      `${this.apiUrl}${environment.api.directory.endpoints.user}${environment.api.directory.endpoints.current}`,
      { headers: this.getAuthHeader() }
    ).pipe(
      tap(user => this.updateState({ user: user, isLoggedIn: true })),
      catchError(error => this.handleError(error, 'Failed to fetch user'))
    );
  }

  private updateState(partialState: Partial<AuthState>) {
    this.stateSubject.next({ ...this.stateSubject.value, ...partialState });
  }

  private doRefreshToken(): Observable<void> {
    if (!this.tokenService.refreshToken || this.isTokenExpired(this.tokenService.refreshToken)) {
      this.resetAuthState();
      return throwError(() => new Error('Refresh token expired'));
    }

    return this.http.post<{ accessToken: string, refreshToken: string }>(
      `${this.apiUrl}/refresh-token`,
      null,
      { headers: new HttpHeaders({ Authorization: `Bearer ${this.tokenService.refreshToken}` }) }
    ).pipe(
      tap(({ accessToken, refreshToken }) => {
        this.tokenService.accessToken = accessToken;
        this.tokenService.refreshToken = refreshToken;
      }),
      map(() => void 0),
      catchError(error => {
        this.resetAuthState();
        return this.handleError(error, 'Token refresh failed');
      })
    );
  }

  private initializeAuthState() {
    if (this.tokenService.accessToken && !this.isTokenExpired(this.tokenService.accessToken)) {
      this.fetchCurrentUser().subscribe();
    } else {
      this.resetAuthState();
    }
  }

  private startTokenMonitoring() {
    timer(0, this.tokenCheckInterval).subscribe(() => {
      if (this.tokenService.accessToken && this.isTokenExpiringSoon()) {
        this.doRefreshToken().subscribe();
      }
    });
  }

  private getAuthHeader(): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${this.tokenService.accessToken}` });
  }

  private isTokenExpired(token: string | null): boolean {
    if (!token) return true;
    const expiration = this.getTokenExpiration(token);
    return expiration < Date.now();
  }

  private isTokenExpiringSoon(): boolean {
    if (!this.tokenService.accessToken) return true;
    const expiration = this.getTokenExpiration(this.tokenService.accessToken);
    return expiration - Date.now() < this.tokenRefreshBuffer;
  }

  private getTokenExpiration(token: string): number {
    try {
      const { exp } = JSON.parse(atob(token.split('.')[1]));
      return exp * 1000;
    } catch {
      return 0;
    }
  }

  private resetAuthState() {
    this.tokenService.clearTokens();
    this.updateState({ isLoggedIn: false, user: null });
    this.router.navigate(['/login']);
  }

  private handleError(error: HttpErrorResponse, message: string): Observable<never> {
    console.error(`${message}:`, error);
    if (error.status === 401) this.resetAuthState();
    return throwError(() => error);
  }
}
