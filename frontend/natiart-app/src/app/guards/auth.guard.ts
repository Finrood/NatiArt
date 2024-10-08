import {CanActivateFn, Router, UrlTree} from '@angular/router';
import {inject} from '@angular/core';
import {AuthenticationService} from '../service/authentication.service';
import {RedirectService} from "../service/redirect.service";

export const authGuard: CanActivateFn = (route, state): boolean | UrlTree => {
  const router = inject(Router);
  const authenticationService = inject(AuthenticationService);
  const redirectService = inject(RedirectService);

  if (authenticationService.getAccessToken()) {
    return true;
  } else {
    redirectService.setRedirectUrl(state.url);
    return router.createUrlTree(['/login']);
  }
};
