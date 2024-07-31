import {CanActivateFn, Router, UrlTree} from '@angular/router';
import {inject} from '@angular/core';
import {AuthenticationService} from '../service/authentication.service';

export const authGuard: CanActivateFn = (route, state): boolean | UrlTree => {
  const router = inject(Router);
  const authenticationService = inject(AuthenticationService);

  if (!authenticationService.getAccessToken()) {
    return router.createUrlTree(['/login']);
  }
  return true;
};
