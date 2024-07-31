import {HttpErrorResponse, HttpHandlerFn, HttpInterceptorFn, HttpRequest} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from "@angular/router";
import { AuthenticationService } from "../service/authentication.service";
import {catchError, switchMap} from "rxjs/operators";
import {throwError} from "rxjs";

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
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
          .then(() => {});
      }
      return throwError(() => error);
    })
  );
};
