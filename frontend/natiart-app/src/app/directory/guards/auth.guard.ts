import {CanActivateFn, UrlTree} from '@angular/router';
import {inject} from '@angular/core';
import {AuthenticationService} from '../service/authentication.service';
import {RedirectService} from "../service/redirect.service";

export const authGuard: CanActivateFn = (route, state): boolean | UrlTree => {
  const authenticationService = inject(AuthenticationService);
  const redirectService = inject(RedirectService);

  const authState = authenticationService.stateSubject.value;

  if (!authState.isLoggedIn) {
    console.log(authState)
    redirectService.setRedirectUrl(state.url);
    return redirectService.getLoginTree();
  }
  return true;
};
