import {HttpClient, HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {Router} from "@angular/router";
import {BehaviorSubject, catchError, filter, first, switchMap, throwError} from "rxjs";
import {TokenService} from "../service/token.service";
import {environment} from "../../../environments/environment";

const viaCepHostname = (): string => {
  try {
    return new URL(environment.api.viaCep.url).hostname;
  } catch {
    return 'viacep.com.br';
  }
};

const isExcludedDomain = (url: string): boolean => {
  try {
    const cleanUrl = url.startsWith('http') ? url : `https://${url}`;
    const hostname = new URL(cleanUrl).hostname;
    return hostname.endsWith(viaCepHostname());
  } catch {
    return false;
  }
};

const isAuthRequest = (url: string): boolean =>
  url.includes('/register-user') || url.includes('/login') || url.includes("/register-ghost-user");

const isRefreshTokenRequest = (url: string): boolean =>
  url.includes('/refresh-token');

const RETRY_HEADER = 'X-Auth-Retried';

let refreshInProgress$: BehaviorSubject<string | null> | null = null;

const performRefresh = (http: HttpClient, tokenService: TokenService): BehaviorSubject<string | null> => {
  if (!refreshInProgress$) {
    const subject = new BehaviorSubject<string | null>(null);
    refreshInProgress$ = subject;

    const refreshTokenValue = tokenService.refreshToken;
    if (!refreshTokenValue) {
      refreshInProgress$ = null;
      subject.error(new Error('No refresh token available'));
      return subject;
    }

    http.post<{ accessToken: string; refreshToken: string }>(
      `${environment.api.directory.url}${environment.api.directory.endpoints.refreshToken}`,
      null,
      {headers: {Authorization: `Bearer ${refreshTokenValue}`}}
    ).subscribe({
      next: (response) => {
        tokenService.accessToken = response.accessToken;
        tokenService.refreshToken = response.refreshToken;
        subject.next(response.accessToken);
        subject.complete();
        if (refreshInProgress$ === subject) {
          refreshInProgress$ = null;
        }
      },
      error: (error) => {
        tokenService.clearTokens();
        if (refreshInProgress$ === subject) {
          refreshInProgress$ = null;
        }
        subject.error(error);
      }
    });
  }
  return refreshInProgress$!;
};

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  if (isExcludedDomain(req.url) || isAuthRequest(req.url) || isRefreshTokenRequest(req.url)) {
    return next(req);
  }

  const tokenService = inject(TokenService);
  const router = inject(Router);
  const http = inject(HttpClient);

  const alreadyRetried = req.headers.has(RETRY_HEADER);

  const cloned = (tokenService.accessToken && !alreadyRetried)
    ? req.clone({setHeaders: {Authorization: `Bearer ${tokenService.accessToken}`}})
    : (alreadyRetried
      ? req.clone({headers: req.headers.delete(RETRY_HEADER)})
      : req);

  return next(cloned).pipe(
    catchError(error => {
      if (error.status === 401 && !isAuthRequest(req.url) && !isRefreshTokenRequest(req.url) && !alreadyRetried) {
        if (!tokenService.refreshToken) {
          router.navigate(['/login']);
          return throwError(() => error);
        }
        return performRefresh(http, tokenService).pipe(
          filter(token => token !== null),
          first(),
          switchMap(token => next(req.clone({
            setHeaders: {Authorization: `Bearer ${token}`, [RETRY_HEADER]: '1'}
          }))),
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
