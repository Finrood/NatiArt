import {inject, Injectable, OnDestroy} from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpHeaders} from "@angular/common/http";
import {Router} from "@angular/router";
import {RoleName, User} from "../models/user.model";
import {environment} from "../../../environments/environment";
import {BehaviorSubject, catchError, Observable, Subject, throwError, timer, Subscription, of, timeout, shareReplay} from "rxjs";
import {Credentials} from "../models/credentials.model";
import {map, switchMap, takeUntil, tap, finalize} from "rxjs/operators";
import {LoginResponse} from "../models/loginResponse.model";
import {TokenService} from "./token.service";
import {reportError} from '../../shared/service/error-reporting.service';

@Injectable({
  providedIn: 'root'
})
export class AuthenticationService implements OnDestroy {
  private readonly apiUrl: string = `${environment.api.directory.url}`;
  private readonly tokenCheckInterval = 60000; // 1 minute
  private readonly tokenRefreshBuffer = 300000; // 5 minutes before expiration
  private readonly inactivityTimeout = 900000; // 15 minutes
  private readonly authInitializationTimeout = 10000;

  private inactivityTimerSubscription: Subscription | undefined;

  private stateSubject = new BehaviorSubject<User | null>(null);

  public readonly currentUser$: Observable<User | null> = this.stateSubject.asObservable();

  private authResolvedSubject = new BehaviorSubject<boolean>(false);

  public readonly authResolved$: Observable<boolean> = this.authResolvedSubject.asObservable();

  public readonly isLoggedIn$: Observable<boolean> = this.currentUser$.pipe(
    map(user => !!user)
  );

  private destroy$ = new Subject<void>();
  private readonly tokenClearSubscription: Subscription;
  private refreshInProgress$: Observable<string> | null = null;
  private sessionGeneration = 0;
  private initialization$: Observable<void> | null = null;
  private initializationIdentity = "";
  private userLookup$: Observable<User> | null = null;
  private userLookupIdentity = "";

  private readonly _http = inject(HttpClient);
  private readonly _router = inject(Router);
  private readonly _tokenService = inject(TokenService);

  constructor() {
    this.tokenClearSubscription = this._tokenService.tokensCleared$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.invalidateSession();
        this.updateState(null);
      });
    timer(0).pipe(
      takeUntil(this.destroy$),
      switchMap(() => this.initializeAuthState()),
      finalize(() => this.authResolvedSubject.next(true)))
      .subscribe();
    this.startTokenMonitoring();
    this.resetInactivityTimer();
  }

  private invalidateSession(): void {
    this.sessionGeneration++;
    this.refreshInProgress$ = null;
    this.initialization$ = null;
    this.userLookup$ = null;
  }

  private establishSession(response: LoginResponse): void {
    this.invalidateSession();
    this.updateState(null);
    this._tokenService.accessToken = response.accessToken;
    this._tokenService.refreshToken = response.refreshToken;
  }

  private clearLocalAuthState() {
    this._tokenService.clearTokens();
  }

  resetInactivityTimer() {
    if (this.inactivityTimerSubscription) {
      this.inactivityTimerSubscription.unsubscribe();
    }

    this.inactivityTimerSubscription = timer(this.inactivityTimeout)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (this.isTokenExpired(this._tokenService.refreshToken)) {
          this.resetAuthStateAndRedirect();
        } else {
          this.doRefreshToken().pipe(takeUntil(this.destroy$)).subscribe({
            error: (error: unknown) => {
              if (this.isAuthenticationFailure(error)) {
                this.resetAuthStateAndRedirect();
              }
            }
          });
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.tokenClearSubscription.unsubscribe();
    if (this.inactivityTimerSubscription) {
      this.inactivityTimerSubscription.unsubscribe();
    }
  }

  login(credentials: Credentials): Observable<User> {
    return this._http.post<LoginResponse>(
      `${this.apiUrl}${environment.api.directory.endpoints.login}`,
      credentials
    ).pipe(
      tap((loginResponse: LoginResponse) => {
        this.establishSession(loginResponse);
      }),
      switchMap(() => this.fetchCurrentUser()),
      catchError(error => this.handleError(error, 'Login failed'))
    );
  }

  setAuthTokensAndUser(loginResponse: LoginResponse): Observable<User> {
    this.establishSession(loginResponse);
    return this.fetchCurrentUser().pipe(
      catchError(error => this.handleError(error, 'Failed to set ghost user authentication'))
    );
  }

  logout(): Observable<void> {
    this.invalidateSession();
    const generation = this.sessionGeneration;
    this.updateState(null);
    return this._http.post<void>(`${this.apiUrl}${environment.api.directory.endpoints.logout}`, {}).pipe(
      tap(() => { if (generation === this.sessionGeneration) this.clearLocalAuthState(); }),
      catchError(error => {
        reportError('logout', error);
        if (generation === this.sessionGeneration) this.clearLocalAuthState();
        return throwError(() => error);
      })
    );
  }

  get isAdmin(): boolean {
    return this.stateSubject.value?.role === RoleName.ADMIN;
  }

  fetchCurrentUser(): Observable<User> {
    const generation = this.sessionGeneration;
    const identity = `${generation}:${this._tokenService.accessToken}`;
    if (this.userLookup$ && this.userLookupIdentity === identity) return this.userLookup$;
    this.userLookupIdentity = identity;
    const headers = this._tokenService.accessToken
      ? new HttpHeaders({ Authorization: `Bearer ${this._tokenService.accessToken}` })
      : new HttpHeaders();

    const lookup$: Observable<User> = this._http.get<User>(
      `${this.apiUrl}${environment.api.directory.endpoints.user}${environment.api.directory.endpoints.current}`,
      { headers }
    ).pipe(
      tap(user => {
        if (generation !== this.sessionGeneration) throw new Error('Authentication session changed during lookup');
        this.updateState(user);
      }),
      catchError(error => this.handleError(error, 'Failed to fetch user', generation)),
      finalize(() => { if (this.userLookup$ === lookup$) this.userLookup$ = null; }),
      shareReplay({bufferSize: 1, refCount: false})
    );
    this.userLookup$ = lookup$;
    return lookup$;
  }

  private updateState(user: User | null) {
    this.stateSubject.next(user);
  }

  private doRefreshToken(): Observable<void> {
    return this.refreshAccessToken().pipe(
      switchMap(() => this.fetchCurrentUser()),
      map(() => void 0)
    );
  }

  public refreshAccessToken(): Observable<string> {
    if (this.refreshInProgress$) {
      return this.refreshInProgress$;
    }

    // The interceptor reaches this method after the API has rejected an access
    // token. The server remains authoritative for refresh-token validity;
    // scheduled and bootstrap paths perform local expiry checks before calling
    // this method.
    if (!this._tokenService.refreshToken) {
      return throwError(() => new Error('Refresh token expired or missing'));
    }

    const generation = this.sessionGeneration;
    const refreshToken = this._tokenService.refreshToken;
    const refresh$ = this._http.post<{ accessToken: string, refreshToken: string }>(
      `${this.apiUrl}${environment.api.directory.endpoints.refreshToken}`,
      null,
      { headers: new HttpHeaders({ Authorization: `Bearer ${refreshToken}` }) }
    ).pipe(
      timeout({first: this.authInitializationTimeout}),
      tap(({ accessToken, refreshToken }) => {
        if (generation !== this.sessionGeneration) {
          throw new Error('Authentication session changed during refresh');
        }
        this._tokenService.accessToken = accessToken;
        this._tokenService.refreshToken = refreshToken;
      }),
      map(({accessToken}) => accessToken),
      finalize(() => { if (this.refreshInProgress$ === refresh$) this.refreshInProgress$ = null; }),
      catchError((error: HttpErrorResponse) => {
        if (generation !== this.sessionGeneration) return throwError(() => new Error('Authentication session changed during refresh'));
        if (this.isAuthenticationFailure(error)) this.clearLocalAuthState();
        return throwError(() => error);
      }),
      shareReplay({bufferSize: 1, refCount: false}),
    );
    this.refreshInProgress$ = refresh$;
    return refresh$;
  }

  public initializeAuthState(): Observable<void> {
    const generation = this.sessionGeneration;
    const accessToken = this._tokenService.accessToken;
    const refreshToken = this._tokenService.refreshToken;
    const identity = `${this.sessionGeneration}:${accessToken}:${refreshToken}`;
    if (this.initialization$ && this.initializationIdentity === identity) return this.initialization$;
    this.initializationIdentity = identity;
    if (this.stateSubject.value && accessToken && !this.isTokenExpired(accessToken)) return of(void 0);

    let initialization$: Observable<void>;
    if (accessToken && !this.isTokenExpired(accessToken)
        && (!this.isAccessTokenExpiringSoon() || !refreshToken || this.isTokenExpired(refreshToken))) {
      initialization$ = this.fetchCurrentUser().pipe(
        map(() => void 0), // Transform to Observable<void>
        catchError(() => {
          if (generation !== this.sessionGeneration) return of(void 0);
          // If fetchCurrentUser fails, try refresh token or reset state
          if (refreshToken && !this.isTokenExpired(refreshToken)) {
            return this.doRefreshToken().pipe(
              map(() => void 0), // Transform to Observable<void>
              catchError((error: unknown) => {
                if (generation === this.sessionGeneration && this.isAuthenticationFailure(error)) {
                  this.resetAuthStateAndRedirect();
                }
                return of(void 0); // Complete the observable
              })
            );
          } else {
            return of(void 0); // Complete the observable
          }
        })
      );
    } else if (refreshToken && !this.isTokenExpired(refreshToken)) {
      initialization$ = this.doRefreshToken().pipe(
        map(() => void 0), // Transform to Observable<void>
        catchError((error: unknown) => {
          if (generation === this.sessionGeneration && this.isAuthenticationFailure(error)) {
            this.resetAuthStateAndRedirect();
          }
          return of(void 0); // Complete the observable
        })
      );
    } else {
      initialization$ = of(void 0);
    }
    this.initialization$ = initialization$.pipe(
      timeout({first: this.authInitializationTimeout}),
      catchError(() => of(void 0)),
      takeUntil(this.destroy$),
      shareReplay({bufferSize: 1, refCount: false})
    );
    return this.initialization$;
  }

  private startTokenMonitoring() {
    timer(this.tokenCheckInterval, this.tokenCheckInterval)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (this._tokenService.accessToken && this.isAccessTokenExpiringSoon() && !this.isTokenExpired(this._tokenService.refreshToken)) {
          this.doRefreshToken().pipe(takeUntil(this.destroy$)).subscribe({
            error: (error: unknown) => {
              if (this.isAuthenticationFailure(error)) {
                this.resetAuthStateAndRedirect();
              }
            }
          });
        } else if (this._tokenService.accessToken && this.isTokenExpired(this._tokenService.accessToken) &&
                   (this.isTokenExpired(this._tokenService.refreshToken) || !this._tokenService.refreshToken)) {
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
    return this.isTokenExpiringSoon(this._tokenService.accessToken, this.tokenRefreshBuffer);
  }

  private isAuthenticationFailure(error: unknown): boolean {
    return error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403);
  }

  private getTokenExpiration(token: string): number {
    try {
      const payload = token.split('.')[1];
      if (!payload) return 0;
      const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64.padEnd(base64.length + (4 - (base64.length % 4)) % 4, '=');
      const decodedPayload = JSON.parse(atob(padded));
      return (decodedPayload.exp || 0) * 1000;
    } catch (e) {
      reportError('token-decoding', e);
      return 0;
    }
  }

  public resetAuthStateAndRedirect() {
    this.clearLocalAuthState();
    if (!window.location.pathname.includes('/login') && !window.location.pathname.includes('/register') && !window.location.pathname.includes('/checkout')) {
      this._router.navigate(['/login']);
    }
  }

  private handleError(error: HttpErrorResponse, message: string, generation = this.sessionGeneration): Observable<never> {
    reportError('authentication', error);
    if (generation === this.sessionGeneration && error.status === 401 && !message.toLowerCase().includes('login failed')) {
      this.resetAuthStateAndRedirect();
    }
    return throwError(() => error);
  }
}
