import {CanActivateFn, Router, UrlTree} from '@angular/router';
import {inject} from '@angular/core';
import {AuthenticationService} from '../service/authentication.service';
import {RedirectService} from "../service/redirect.service";
import {map} from "rxjs";
import {take} from "rxjs/operators";


export const adminGuard: CanActivateFn = (route, state): boolean | UrlTree => {
  const router = inject(Router);
  const authenticationService = inject(AuthenticationService);
  const redirectService = inject(RedirectService);

  const currentUser = authenticationService.authState$.pipe(
    take(1),
    map(value => value)
  );

  if (currentUser) {
    if (authenticationService.isAdmin) {
      return true;
    } else {
      return redirectService.getDashboardTree();
    }
  } else {
    redirectService.setRedirectUrl(state.url);
    router.navigate(['/login'])
      .then(() => {
      });
    return false;
  }
};
