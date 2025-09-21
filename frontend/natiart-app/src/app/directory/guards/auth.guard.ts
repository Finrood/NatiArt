import {CanActivateFn} from '@angular/router';
import {inject} from '@angular/core';
import {AuthenticationService} from '../service/authentication.service';
import {RedirectService} from "../service/redirect.service";
import {map} from "rxjs";

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthenticationService);
  const redirectService = inject(RedirectService);

  // Return an Observable/Promise that resolves after user data is loaded
  console.log(authService.isAdmin)
  return authService.currentUser$.pipe(
    map((authState) => {
      console.log(authState)
      if (!authState) {
        redirectService.setRedirectUrl(state.url);
        return redirectService.getLoginTree();
      }
      return true;
    })
  );
};
