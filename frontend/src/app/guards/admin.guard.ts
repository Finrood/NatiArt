import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { inject } from '@angular/core';
import { TokenService } from '../service/token.service';


export const adminGuard: CanActivateFn = (route, state): boolean | UrlTree => {
  const router = inject(Router);
  const tokenService = inject(TokenService);

  if (!tokenService.getAccessToken()) {
    return router.createUrlTree(['/login']);
  }
  if (!tokenService.isAdmin()) {
    return router.createUrlTree(['/dashboard']);
  }

  return true;
};
