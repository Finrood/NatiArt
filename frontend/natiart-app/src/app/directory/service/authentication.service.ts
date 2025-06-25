import {Injectable, OnDestroy} from '@angular/core'; // Added OnDestroy
import {HttpClient, HttpErrorResponse, HttpHeaders} from "@angular/common/http";
import {Router} from "@angular/router";
import {RoleName, User} from "../models/user.model";
import {environment} from "../../../environments/environment";
import {BehaviorSubject, catchError, Observable, Subject, throwError, timer} from "rxjs"; // Added Subject
import {Credentials} from "../models/credentials.model";
import {map, switchMap, takeUntil, tap} from "rxjs/operators"; // Added takeUntil
import {LoginResponse} from "../models/loginResponse.model";
import {TokenService} from "./token.service";

// Interface AuthState is not strictly needed if we derive isLoggedIn$ from authState$
// interface AuthState {
//   isLoggedIn: boolean;
//   user: User | null;
// }

@Injectable({
  providedIn: 'root'
})
export class AuthenticationService implements OnDestroy { // Implemented OnDestroy
  private readonly apiUrl: string = `${environment.api.directory.url}`;
  private readonly tokenCheckInterval = 60000; // 1 minute
  private readonly tokenRefreshBuffer = 300000; // 5 minutes before expiration

  // stateSubject holds the current User object or null
  private stateSubject = new BehaviorSubject<User | null>(null);

  // Public observable for the User object
  public readonly currentUser$: Observable<User | null> = this.stateSubject.asObservable();

  // Public observable for the login status, derived from currentUser$
  public readonly isLoggedIn$: Observable<boolean> = this.currentUser$.pipe(
    map(user => !!user) // Converts User|null to boolean
  );

  private destroy$ = new Subject<void>(); // For cleaning up subscriptions within this service

  constructor(private http: HttpClient, private router: Router, private tokenService: TokenService) {
    this.initializeAuthState(); // Call this in constructor
    this.startTokenMonitoring(); // Call this in constructor
  }

  // ngOnInit is typically for components. For services, constructor or a manual init method is common.
  // If you need ngOnInit, ensure your service is provided in a way that it gets instantiated at the right time.
  // For a root-provided service, constructor is fine.

  ngOnDestroy(): void { // Implement OnDestroy
    this.destroy$.next();
    this.destroy$.complete();
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
      switchMap(() => this.fetchCurrentUser()), // fetchCurrentUser will update stateSubject
      catchError(error => this.handleError(error, 'Login failed'))
    );
  }

  logout(): Observable<void> {
    // No need to pass null if the endpoint doesn't expect a body for logout
    return this.http.post<void>(`${this.apiUrl}${environment.api.directory.endpoints.logout}`, {}).pipe(
      tap(() => this.resetAuthState()),
      catchError(error => this.handleError(error, 'Logout failed'))
    );
  }

  get isAdmin(): boolean {
    return this.stateSubject.value?.role === RoleName.ADMIN;
  }

  fetchCurrentUser(): Observable<User> {
    // Ensure Authorization header is set if tokens exist
    const headers = this.tokenService.accessToken
      ? new HttpHeaders({ Authorization: `Bearer ${this.tokenService.accessToken}` })
      : new HttpHeaders();

    return this.http.get<User>(
      `${this.apiUrl}${environment.api.directory.endpoints.user}${environment.api.directory.endpoints.current}`,
      { headers } // Pass headers
    ).pipe(
      tap(user => {
        this.updateState(user);
      }),
      catchError(error => {
        // If fetching current user fails (e.g., 401), it likely means token is invalid/expired
        if (error.status === 401) {
          this.resetAuthState(); // Reset state if unauthorized
        }
        return this.handleError(error, 'Failed to fetch user');
      })
    );
  }

  private updateState(user: User | null) { // Parameter changed to User | null
    this.stateSubject.next(user);
  }

  private doRefreshToken(): Observable<void> {
    if (!this.tokenService.refreshToken || this.isTokenExpired(this.tokenService.refreshToken)) {
      this.resetAuthState();
      return throwError(() => new Error('Refresh token expired or missing'));
    }

    return this.http.post<{ accessToken: string, refreshToken: string }>(
      `${this.apiUrl}/refresh-token`, // Assuming this is the correct refresh endpoint
      null, // Body can be null if not required by backend
      { headers: new HttpHeaders({ Authorization: `Bearer ${this.tokenService.refreshToken}` }) }
    ).pipe(
      tap(({ accessToken, refreshToken }) => {
        this.tokenService.accessToken = accessToken;
        this.tokenService.refreshToken = refreshToken;
        // Optionally, re-fetch current user after successful token refresh to update roles/details
        this.fetchCurrentUser().pipe(takeUntil(this.destroy$)).subscribe();
      }),
      map(() => void 0), // Transform to Observable<void>
      catchError(error => {
        this.resetAuthState(); // Critical: If refresh fails, user is logged out
        return this.handleError(error, 'Token refresh failed');
      })
    );
  }

  private initializeAuthState(): void { // Return type void
    if (this.tokenService.accessToken && !this.isTokenExpired(this.tokenService.accessToken)) {
      // Subscribe to fetchCurrentUser and manage the subscription
      this.fetchCurrentUser().pipe(takeUntil(this.destroy$)).subscribe({
        // Error handling is done within fetchCurrentUser and handleError
      });
    } else if (this.tokenService.refreshToken && !this.isTokenExpired(this.tokenService.refreshToken)) {
      // If access token is gone/expired but refresh token is good, try to refresh
      this.doRefreshToken().pipe(takeUntil(this.destroy$)).subscribe({
        error: () => { /* Error handled in doRefreshToken, state reset there */ }
      });
    }
    else {
      this.resetAuthState(); // Ensure clean state if no valid tokens
    }
  }

  private startTokenMonitoring() {
    timer(0, this.tokenCheckInterval)
      .pipe(takeUntil(this.destroy$)) // Manage this timer subscription
      .subscribe(() => {
        if (this.tokenService.accessToken && this.isTokenExpiringSoon() && !this.isTokenExpired(this.tokenService.refreshToken)) {
          // Only refresh if refresh token is still valid
          this.doRefreshToken().pipe(takeUntil(this.destroy$)).subscribe({
            error: () => { /* Error handled in doRefreshToken, state reset there */ }
          });
        } else if (this.tokenService.accessToken && this.isTokenExpired(this.tokenService.accessToken)) {
          // Access token expired, and refresh might also be expired or not working
          this.resetAuthState();
        }
      });
  }

  private isTokenExpired(token: string | null): boolean {
    if (!token) return true;
    const expiration = this.getTokenExpiration(token);
    return expiration < Date.now();
  }

  private isTokenExpiringSoon(): boolean {
    if (!this.tokenService.accessToken) return false; // If no access token, not expiring soon
    const expiration = this.getTokenExpiration(this.tokenService.accessToken);
    return expiration - Date.now() < this.tokenRefreshBuffer;
  }

  private getTokenExpiration(token: string): number {
    try {
      const payload = token.split('.')[1];
      if (!payload) return 0;
      const decodedPayload = JSON.parse(atob(payload));
      return (decodedPayload.exp || 0) * 1000; // exp is in seconds
    } catch (e) {
      console.error("Error decoding token: ", e);
      return 0;
    }
  }

  private resetAuthState() {
    this.tokenService.clearTokens();
    this.updateState(null); // This will make isLoggedIn$ emit false
    // Only navigate if not already on login/register page to avoid navigation loops
    if (!this.router.url.includes('/login') && !this.router.url.includes('/register')) {
      this.router.navigate(['/login']);
    }
  }

  private handleError(error: HttpErrorResponse, message: string): Observable<never> {
    console.error(`${message}:`, error.message); // Log error message
    // Consider specific error handling, e.g., for 401 Unauthorized
    if (error.status === 401 && !message.toLowerCase().includes('login failed')) { // Avoid resetting state during login attempt itself
      this.resetAuthState();
    }
    return throwError(() => new Error(`${message}. Status: ${error.status}. Details: ${error.message}`));
  }
}
