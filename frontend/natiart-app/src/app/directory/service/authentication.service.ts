import {Injectable, OnInit} from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpHeaders} from "@angular/common/http";
import {Router} from "@angular/router";
import {RoleName, User} from "../models/user.model";
import {environment} from "../../../environments/environment";
import {BehaviorSubject, catchError, Observable, of, throwError, timer} from "rxjs";
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
export class AuthenticationService implements OnInit {
  private readonly apiUrl: string = `${environment.api.directory.url}`;
  private readonly tokenCheckInterval = 60000; // 1 minute
  private readonly tokenRefreshBuffer = 300000; // 5 minutes before expiration

  private initialState: AuthState = {
    isLoggedIn: !!this.tokenService.accessToken,
    user: null
  };

  private stateSubject = new BehaviorSubject<User | null>(null);
  authState$ = this.stateSubject!.asObservable();

  constructor(private http: HttpClient, private router: Router, private tokenService: TokenService) {

  }

  ngOnInit() {
    this.initializeAuthState();
    console.log('inited')
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
    console.log(this.stateSubject.value)
    return this.stateSubject.value?.role === RoleName.ADMIN;
  }

  fetchCurrentUser(): Observable<User> {
    return this.http.get<User>(
      `${this.apiUrl}${environment.api.directory.endpoints.user}${environment.api.directory.endpoints.current}`,
    ).pipe(
      tap(user => {
        console.log(user)
        this.updateState(user)
        console.log(this.stateSubject.value)
      }),
      catchError(error => this.handleError(error, 'Failed to fetch user'))
    );
  }

  private updateState(authState: User | null) {
    console.log("updating to " + authState)
    this.stateSubject.next(authState);
    console.log(this.stateSubject.value)
  }

  private doRefreshToken(): Observable<void> {
    console.log("Refreshing token")
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

  initializeAuthState(): Observable<void> {
    if (this.tokenService.accessToken && !this.isTokenExpired(this.tokenService.accessToken)) {
      return this.fetchCurrentUser().pipe(
        map(() => void 0) // Convert to Observable<void>
      );
    } else {
      console.log('access token expired')
      this.resetAuthState();
    }
    return of(void 0);
  }

  private startTokenMonitoring() {
    timer(0, this.tokenCheckInterval).subscribe(() => {
      if (this.tokenService.accessToken && this.isTokenExpiringSoon()) {
        this.doRefreshToken().subscribe();
      }
    });
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
    console.log("resetting auth state")
    this.tokenService.clearTokens();
    this.updateState(null);
    this.router.navigate(['/login']);
  }

  private handleError(error: HttpErrorResponse, message: string): Observable<never> {
    console.error(`${message}:`, error);
    if (error.status === 401) this.resetAuthState();
    return throwError(() => error);
  }
}
