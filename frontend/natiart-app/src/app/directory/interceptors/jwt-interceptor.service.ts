import {HttpHeaders, HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {Router} from "@angular/router";
import {catchError, throwError} from "rxjs";
import {TokenService} from "../service/token.service";

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

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  if (isExcludedDomain(req.url) || isAuthRequest(req.url)) {
    return next(req);
  }

  const tokenService = inject(TokenService);
  const router = inject(Router);

  const cloned = req.clone({
    headers: new HttpHeaders({
      ...req.headers.keys().reduce((acc, key) => {
        acc[key] = req.headers.getAll(key);
        return acc;
      }, {} as { [key: string]: any }),
      Authorization: `Bearer ${tokenService.accessToken}`
    })
  });

  return next(cloned).pipe(
    catchError(error => {
      if (error.status === 401 && !isAuthRequest(req.url)) {
        console.log("ERRROOOR")
        //router.navigate(['/logout'])
      }
      return throwError(() => error);
    })
  );
};
