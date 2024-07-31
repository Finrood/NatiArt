import {CanActivateFn, Router, UrlTree} from '@angular/router';
import {inject} from '@angular/core';
import {AuthenticationService} from '../service/authentication.service';


export const adminGuard: CanActivateFn = (route, state): boolean | UrlTree => {
  const router = inject(Router);
  const authenticationService = inject(AuthenticationService);

  if (!authenticationService.getAccessToken()) {
    return router.createUrlTree(['/login']);
  }
  if (!authenticationService.isAdmin()) {
    return router.createUrlTree(['/dashboard']);
  }

  return true;
};
