import {CanActivateFn, Router, UrlTree} from '@angular/router';
import {inject} from '@angular/core';
import {AuthenticationService} from '../service/authentication.service';
import {RedirectService} from "../service/redirect.service";
import {filter, map, switchMap, take} from "rxjs";
import { Observable } from 'rxjs';


export const adminGuard: CanActivateFn = (route, state): Observable<boolean | UrlTree> => {
  const router = inject(Router);
  const authenticationService = inject(AuthenticationService);
  const redirectService = inject(RedirectService);

  // Bootstrap is non-blocking, so the current user may still be `null` while the initial
  // token validation is in flight. Wait for the auth resolution to complete before
  // deciding, otherwise admins get bounced to the dashboard/login on a cold page load.
  return authenticationService.authResolved$.pipe(
    filter(resolved => resolved),
    take(1),
    switchMap(() => authenticationService.currentUser$.pipe(take(1))),
    map(user => {
      if (user && authenticationService.isAdmin) { // Check if user exists and is admin
        return true;
      } else {
        // If not admin or not logged in, redirect
        redirectService.setRedirectUrl(state.url);
        // If user is logged in but not admin, redirect to dashboard. Otherwise, to login.
        return user ? redirectService.getDashboardTree() : redirectService.getLoginTree();
      }
    })
  );
};

