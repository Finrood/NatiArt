import {HttpContextToken, HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {Router} from "@angular/router";
import {catchError, switchMap, throwError} from "rxjs";
import {TokenService} from "../service/token.service";
import {environment} from "../../../environments/environment";
import {AuthenticationService} from "../service/authentication.service";

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
  return [endpoints.login, endpoints.registerUser];
};

const isAuthRequest = (url: string): boolean =>
  isEndpoint(url, directoryAuthEndpoints());

const isRefreshTokenRequest = (url: string): boolean =>
  isEndpoint(url, [environment.api.directory.endpoints.refreshToken]);

const isLogoutRequest = (url: string): boolean =>
  isEndpoint(url, [environment.api.directory.endpoints.logout]);

export const AUTH_RETRY_CONTEXT = new HttpContextToken<boolean>(() => false);

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  if (isExcludedDomain(req.url) || isAuthRequest(req.url) || isRefreshTokenRequest(req.url)) {
    return next(req);
  }

  const tokenService = inject(TokenService);
  const router = inject(Router);
  const authenticationService = inject(AuthenticationService);

  const alreadyRetried = req.context.get(AUTH_RETRY_CONTEXT);

  const cloned = (tokenService.accessToken && !alreadyRetried)
    ? req.clone({setHeaders: {Authorization: `Bearer ${tokenService.accessToken}`}})
    : req;

  return next(cloned).pipe(
    catchError(error => {
      if (error.status === 401 && !isAuthRequest(req.url) && !isRefreshTokenRequest(req.url) && !alreadyRetried) {
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
        return authenticationService.refreshAccessToken().pipe(
          catchError(refreshError => {
            if (refreshError.status === 401 || refreshError.status === 403) {
              router.navigate(['/login']);
            }
            return throwError(() => refreshError);
          }),
          switchMap(token => next(req.clone({
            setHeaders: {Authorization: `Bearer ${token}`},
            context: req.context.set(AUTH_RETRY_CONTEXT, true),
          }))),
        );
      }
      return throwError(() => error);
    })
  );
};
