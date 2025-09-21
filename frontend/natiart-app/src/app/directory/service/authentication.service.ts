import {Injectable, OnDestroy} from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpHeaders} from "@angular/common/http";
import {Router} from "@angular/router";
import {RoleName, User} from "../models/user.model";
import {environment} from "../../../environments/environment";
import {BehaviorSubject, catchError, Observable, Subject, throwError, timer, Subscription, of} from "rxjs";
import {Credentials} from "../models/credentials.model";
import {map, switchMap, takeUntil, tap} from "rxjs/operators";
import {LoginResponse} from "../models/loginResponse.model";
import {TokenService} from "./token.service";

@Injectable({
  providedIn: 'root'
})
export class AuthenticationService implements OnDestroy {
  private readonly apiUrl: string = `${environment.api.directory.url}`;
  private readonly tokenCheckInterval = 60000; // 1 minute
  private readonly tokenRefreshBuffer = 300000; // 5 minutes before expiration
  private readonly refreshTokenRefreshBuffer = 86400000 * 7; // 7 days before refresh token expiration
  private readonly inactivityTimeout = 900000; // 15 minutes

  private inactivityTimerSubscription: Subscription | undefined;

  private stateSubject = new BehaviorSubject<User | null>(null);

  public readonly currentUser$: Observable<User | null> = this.stateSubject.asObservable();

  public readonly isLoggedIn$: Observable<boolean> = this.currentUser$.pipe(
    map(user => !!user)
  );

  private destroy$ = new Subject<void>();

  constructor(private http: HttpClient, private router: Router, private tokenService: TokenService) {
    this.initializeAuthState();
    this.startTokenMonitoring();
    this.resetInactivityTimer();
  }

  private clearLocalAuthState() {
    this.tokenService.clearTokens();
    this.updateState(null);
  }

  resetInactivityTimer() {
    if (this.inactivityTimerSubscription) {
      this.inactivityTimerSubscription.unsubscribe();
    }

    this.inactivityTimerSubscription = timer(this.inactivityTimeout)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (this.isTokenExpired(this.tokenService.refreshToken)) {
          this.resetAuthStateAndRedirect();
        } else {
          this.doRefreshToken().pipe(takeUntil(this.destroy$)).subscribe({
            error: () => {
              this.resetAuthStateAndRedirect();
            }
          });
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.inactivityTimerSubscription) {
      this.inactivityTimerSubscription.unsubscribe();
    }
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

  setAuthTokensAndUser(loginResponse: LoginResponse): Observable<User> {
    this.tokenService.accessToken = loginResponse.accessToken;
    this.tokenService.refreshToken = loginResponse.refreshToken;
    return this.fetchCurrentUser().pipe(
      catchError(error => this.handleError(error, 'Failed to set ghost user authentication'))
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}${environment.api.directory.endpoints.logout}`, {}).pipe(
      tap(() => this.clearLocalAuthState()),
      catchError(error => {
        console.error('Logout API call failed, clearing local state anyway:', error);
        this.clearLocalAuthState();
        return throwError(() => error);
      })
    );
  }

  get isAdmin(): boolean {
    return this.stateSubject.value?.role === RoleName.ADMIN;
  }

  fetchCurrentUser(): Observable<User> {
    const headers = this.tokenService.accessToken
      ? new HttpHeaders({ Authorization: `Bearer ${this.tokenService.accessToken}` })
      : new HttpHeaders();

    return this.http.get<User>(
      `${this.apiUrl}${environment.api.directory.endpoints.user}${environment.api.directory.endpoints.current}`,
      { headers }
    ).pipe(
      tap(user => {
        this.updateState(user);
      }),
      catchError(error => {
        if (error.status === 401) {
          this.resetAuthStateAndRedirect();
        }
        return this.handleError(error, 'Failed to fetch user');
      })
    );
  }

  private updateState(user: User | null) {
    this.stateSubject.next(user);
  }

  private doRefreshToken(): Observable<void> {
    if (!this.tokenService.refreshToken || this.isTokenExpired(this.tokenService.refreshToken)) {
      this.resetAuthStateAndRedirect();
      return throwError(() => new Error('Refresh token expired or missing'));
    }

    return this.http.post<{ accessToken: string, refreshToken: string }>(
      `${this.apiUrl}/refresh-token`,
      null,
      { headers: new HttpHeaders({ Authorization: `Bearer ${this.tokenService.refreshToken}` }) }
    ).pipe(
      tap(({ accessToken, refreshToken }) => {
        this.tokenService.accessToken = accessToken;
        this.tokenService.refreshToken = refreshToken;
        this.fetchCurrentUser().pipe(takeUntil(this.destroy$)).subscribe();
      }),
      map(() => void 0),
      catchError(error => {
        this.resetAuthStateAndRedirect();
        return this.handleError(error, 'Token refresh failed');
      })
    );
  }

  public initializeAuthState(): Observable<void> {
    const accessToken = this.tokenService.accessToken;
    const refreshToken = this.tokenService.refreshToken;

    console.log('initializeAuthState: Access Token:', accessToken);
    console.log('initializeAuthState: Refresh Token:', refreshToken);
    console.log('initializeAuthState: Access Token Expired:', this.isTokenExpired(accessToken));
    console.log('initializeAuthState: Refresh Token Expired:', this.isTokenExpired(refreshToken));

    if (accessToken && !this.isTokenExpired(accessToken)) {
      return this.fetchCurrentUser().pipe(
        map(() => void 0), // Transform to Observable<void>
        catchError(() => {
          // If fetchCurrentUser fails, try refresh token or reset state
          if (refreshToken && !this.isTokenExpired(refreshToken)) {
            return this.doRefreshToken().pipe(
              map(() => void 0), // Transform to Observable<void>
              catchError(() => {
                this.resetAuthStateAndRedirect();
                return of(void 0); // Complete the observable
              })
            );
          } else {
            this.resetAuthStateAndRedirect();
            return of(void 0); // Complete the observable
          }
        })
      );
    } else if (refreshToken && !this.isTokenExpired(refreshToken)) {
      return this.doRefreshToken().pipe(
        map(() => void 0), // Transform to Observable<void>
        catchError(() => {
          this.resetAuthStateAndRedirect();
          return of(void 0); // Complete the observable
        })
      );
    } else {
      this.resetAuthStateAndRedirect();
      return of(void 0); // Complete the observable immediately if no tokens
    }
  }

  private startTokenMonitoring() {
    timer(0, this.tokenCheckInterval)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (this.tokenService.accessToken && this.isAccessTokenExpiringSoon() && !this.isTokenExpired(this.tokenService.refreshToken)) {
          this.doRefreshToken().pipe(takeUntil(this.destroy$)).subscribe({});
        } else if (this.tokenService.refreshToken && this.isRefreshTokenExpiringSoon()) {
          this.doRefreshToken().pipe(takeUntil(this.destroy$)).subscribe({});
        } else if (this.tokenService.accessToken && this.isTokenExpired(this.tokenService.accessToken) &&
                   (this.isTokenExpired(this.tokenService.refreshToken) || !this.tokenService.refreshToken)) {
          this.resetAuthStateAndRedirect();
        }
      });
  }

  private isTokenExpired(token: string | null): boolean {
    if (!token) return true;
    const expiration = this.getTokenExpiration(token);
    return expiration < Date.now();
  }

  private isTokenExpiringSoon(token: string | null, buffer: number): boolean {
    if (!token) return false;
    const expiration = this.getTokenExpiration(token);
    return expiration - Date.now() < buffer;
  }

  private isAccessTokenExpiringSoon(): boolean {
    return this.isTokenExpiringSoon(this.tokenService.accessToken, this.tokenRefreshBuffer);
  }

  private isRefreshTokenExpiringSoon(): boolean {
    return this.isTokenExpiringSoon(this.tokenService.refreshToken, this.refreshTokenRefreshBuffer);
  }

  private getTokenExpiration(token: string): number {
    try {
      const payload = token.split('.')[1];
      if (!payload) return 0;
      const decodedPayload = JSON.parse(atob(payload));
      const expiration = (decodedPayload.exp || 0) * 1000;
      console.log('getTokenExpiration: Decoded Payload:', decodedPayload);
      console.log('getTokenExpiration: Expiration Time (ms):', expiration);
      return expiration;
    } catch (e) {
      console.error("Error decoding token: ", e);
      return 0;
    }
  }

  public resetAuthStateAndRedirect() {
    this.clearLocalAuthState();
    console.log('resetAuthStateAndRedirect called. Current window.location.pathname:', window.location.pathname);
    if (!window.location.pathname.includes('/login') && !window.location.pathname.includes('/register') && !window.location.pathname.includes('/checkout')) {
      this.router.navigate(['/login']);
    }
  }

  private handleError(error: HttpErrorResponse, message: string): Observable<never> {
    console.error(`${message}:`, error.message);
    if (error.status === 401 && !message.toLowerCase().includes('login failed')) {
      this.resetAuthStateAndRedirect();
    }
    return throwError(() => new Error(`${message}. Status: ${error.status}. Details: ${error.message}`));
  }
}