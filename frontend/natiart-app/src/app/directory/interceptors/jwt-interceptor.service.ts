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

const isEndpoint = (url: string, endpoints: string[]): boolean => {
  const base: string = environment.api.directory.url;
  for (const endpoint of endpoints) {
    if (url === endpoint || url === `${base}${endpoint}`) {
      return true;
    }
    try {
      if (/^https?:\/\//i.test(url)) {
        const parsed: URL = new URL(url);
        const baseParsed: URL = new URL(base);
        if (parsed.origin === baseParsed.origin && parsed.pathname === endpoint) {
          return true;
        }
      } else {
        const parsed: URL = new URL(url, 'http://placeholder.local');
        if (parsed.pathname === endpoint) {
          return true;
        }
      }
    } catch {
      // Unparseable URL: fail closed, it is not an exempt endpoint.
    }
  }
  return false;
};

const directoryAuthEndpoints = (): string[] => {
  const endpoints = environment.api.directory.endpoints;
  return [endpoints.login, endpoints.registerUser, endpoints.registerGhostUser];
};

const isAuthRequest = (url: string): boolean =>
  isEndpoint(url, directoryAuthEndpoints());

const isRefreshTokenRequest = (url: string): boolean =>
  isEndpoint(url, [environment.api.directory.endpoints.refreshToken]);

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
