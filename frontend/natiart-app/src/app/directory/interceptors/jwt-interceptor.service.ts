import {HttpClient, HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {Router} from "@angular/router";
import {BehaviorSubject, catchError, filter, first, switchMap, throwError} from "rxjs";
import {TokenService} from "../service/token.service";
import {environment} from "../../../environments/environment";

const EXCLUDED_DOMAINS = ['viacep.com.br'];

const isExcludedDomain = (url: string): boolean => {
  try {
    const cleanUrl = url.startsWith('http') ? url : `https://${url}`;
    const hostname = new URL(cleanUrl).hostname;
    return EXCLUDED_DOMAINS.some(domain => hostname.endsWith(domain));
  } catch {
    return false;
  }
};

const isAuthRequest = (url: string): boolean =>
  url.includes('/register-user') || url.includes('/login') || url.includes("/register-ghost-user");

const isRefreshTokenRequest = (url: string): boolean =>
  url.includes('/refresh-token');

let refreshInProgress$: BehaviorSubject<string | null> | null = null;

const performRefresh = (http: HttpClient, tokenService: TokenService): BehaviorSubject<string | null> => {
  if (!refreshInProgress$) {
    refreshInProgress$ = new BehaviorSubject<string | null>(null);

    const refreshTokenValue = tokenService.refreshToken;
    if (!refreshTokenValue) {
      refreshInProgress$.error(new Error('No refresh token available'));
      refreshInProgress$ = null;
      return new BehaviorSubject<string | null>(null);
    }

    http.post<{ accessToken: string; refreshToken: string }>(
      `${environment.api.directory.url}/refresh-token`,
      null,
      {headers: {Authorization: `Bearer ${refreshTokenValue}`}}
    ).subscribe({
      next: (response) => {
        tokenService.accessToken = response.accessToken;
        tokenService.refreshToken = response.refreshToken;
        refreshInProgress$?.next(response.accessToken);
        refreshInProgress$?.complete();
        refreshInProgress$ = null;
      },
      error: (error) => {
        const pending = refreshInProgress$;
        refreshInProgress$ = null;
        tokenService.clearTokens();
        pending?.error(error);
      }
    });
  }
  return refreshInProgress$;
};

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  if (isExcludedDomain(req.url) || isAuthRequest(req.url) || isRefreshTokenRequest(req.url)) {
    return next(req);
  }

  const tokenService = inject(TokenService);
  const router = inject(Router);
  const http = inject(HttpClient);

  const cloned = tokenService.accessToken
    ? req.clone({setHeaders: {Authorization: `Bearer ${tokenService.accessToken}`}})
    : req;

  return next(cloned).pipe(
    catchError(error => {
      if (error.status === 401 && !isAuthRequest(req.url) && !isRefreshTokenRequest(req.url)) {
        if (!tokenService.refreshToken) {
          router.navigate(['/login']);
          return throwError(() => error);
        }
        return performRefresh(http, tokenService).pipe(
          filter(token => token !== null),
          first(),
          switchMap(token => next(req.clone({setHeaders: {Authorization: `Bearer ${token}`}}))),
          catchError(refreshError => {
            router.navigate(['/login']);
            return throwError(() => refreshError);
          })
        );
      }
      return throwError(() => error);
    })
  );
};
