import {HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {Router} from "@angular/router";
import {AuthenticationService} from "../service/authentication.service";
import {catchError} from "rxjs/operators";
import {throwError} from "rxjs";

interface ExcludedDomain {
  hostname: string;
}

const EXCLUDED_DOMAINS: ExcludedDomain[] = [
  {hostname: 'viacep.com.br'},
];

const isExcludedDomain = (url: string): boolean => {
  try {
    const urlObj = new URL(url);
    return EXCLUDED_DOMAINS.some(domain =>
      urlObj.hostname === domain.hostname
    );
  } catch {
    return EXCLUDED_DOMAINS.some(domain =>
      url.includes(domain.hostname)
    );
  }
};

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  if (isExcludedDomain(req.url)) {
    return next(req);
  }

  const router = inject(Router);
  const authService = inject(AuthenticationService);

  if (authService.isAccessTokenExpired()) {
    authService.refreshToken();
  }

  const accessToken = authService.getAccessToken();
  const cloned = req.clone({
    headers: req.headers.set('Authorization', `Bearer ${accessToken}`)
  });

  return next(cloned).pipe(
    catchError((error) => {
      if (error.status === 401) {
        router.navigate(['/logout'])
          .then(() => {
          });
      }
      return throwError(() => error);
    })
  );
};
