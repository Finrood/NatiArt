import {CanActivateFn} from '@angular/router';
import {inject} from '@angular/core';
import {AuthenticationService} from '../service/authentication.service';
import {RedirectService} from "../service/redirect.service";
import {map, switchMap, take} from "rxjs";

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthenticationService);
  const redirectService = inject(RedirectService);

  return authService.authResolved$.pipe(
    take(1),
    switchMap(() => authService.currentUser$.pipe(take(1))),
    map((authState) => {
      if (!authState) {
        redirectService.setRedirectUrl(state.url);
        return redirectService.getLoginTree();
      }
      return true;
    })
  );
};
