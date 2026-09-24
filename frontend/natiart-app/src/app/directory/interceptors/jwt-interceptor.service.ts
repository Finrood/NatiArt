import {HttpClient, HttpContextToken, HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {Router} from "@angular/router";
import {BehaviorSubject, catchError, filter, first, switchMap, throwError, timeout} from "rxjs";
import {TokenService} from "../service/token.service";
import {environment} from "../../../environments/environment";

const parsedUrl = (url: string): URL | null => {
  try {
    return new URL(url, window.location.origin);
  } catch {
    return null;
  }
};

const basePath = (url: URL): string => url.pathname.replace(/\/+$/, '');

const isWithinApi = (request: URL, apiUrl: string): boolean => {
  const api = parsedUrl(apiUrl);
  if (!api || request.origin !== api.origin) {
    return false;
  }
  const path = basePath(api);
  return path === '' || request.pathname === path || request.pathname.startsWith(`${path}/`);
};

const isEndpoint = (url: string, endpoints: string[]): boolean => {
  const request = parsedUrl(url);
  const directory = parsedUrl(environment.api.directory.url);
  if (!request || !directory || !isWithinApi(request, environment.api.directory.url)) {
    return false;
  }
  return endpoints.some(endpoint => request.pathname === `${basePath(directory)}/${endpoint.replace(/^\/+/, '')}`);
};

const isConfiguredApiUrl = (url: string): boolean => {
  const request = parsedUrl(url);
  return request !== null && [environment.api.directory.url, environment.api.product.url]
    .some(apiUrl => isWithinApi(request, apiUrl));
};

const directoryAuthEndpoints = (): string[] => {
  const endpoints = environment.api.directory.endpoints;
  return [endpoints.login, endpoints.registerUser];
};

const isAuthRequest = (url: string): boolean =>
  isEndpoint(url, directoryAuthEndpoints());

const isRefreshTokenRequest = (url: string): boolean =>
  isEndpoint(url, [environment.api.directory.endpoints.refreshToken]);

const isLogoutRequest = (url: string): boolean =>
  isEndpoint(url, [environment.api.directory.endpoints.logout]);

const AUTH_RETRIED = new HttpContextToken<boolean>(() => false);
const REFRESH_TIMEOUT_MS = 10000;

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
    ).pipe(timeout({first: REFRESH_TIMEOUT_MS})).subscribe({
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
  if (!isConfiguredApiUrl(req.url) || isAuthRequest(req.url) || isRefreshTokenRequest(req.url)) {
    return next(req);
  }

  const tokenService = inject(TokenService);
  const router = inject(Router);
  const http = inject(HttpClient);

  const alreadyRetried = req.context.get(AUTH_RETRIED);

  // A caller-supplied credential belongs to the caller. The interceptor only
  // manages credentials for requests that do not already carry Authorization.
  if (req.headers.has('Authorization') && !alreadyRetried) {
    return next(req);
  }

  const cloned = (tokenService.accessToken && !alreadyRetried)
    ? req.clone({setHeaders: {Authorization: `Bearer ${tokenService.accessToken}`}})
    : req;

  return next(cloned).pipe(
    catchError(error => {
      if (error.status === 401 && !alreadyRetried) {
        if (isLogoutRequest(req.url)) {
          // Explicit logout must never mint fresh tokens: end the local
          // session instead of refreshing-then-retrying the signout.
          tokenService.clearTokens();
          router.navigate(['/login']);
          return throwError(() => error);
        }
        if (!tokenService.refreshToken) {
          router.navigate(['/login']);
          return throwError(() => error);
        }
        return performRefresh(http, tokenService).pipe(
          filter(token => token !== null),
          first(),
          switchMap(token => next(req.clone({
            setHeaders: {Authorization: `Bearer ${token}`},
            context: req.context.set(AUTH_RETRIED, true)
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
