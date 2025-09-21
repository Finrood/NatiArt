import {CanActivateFn, Router, UrlTree} from '@angular/router';
import {inject} from '@angular/core';
import {AuthenticationService} from '../service/authentication.service';
import {RedirectService} from "../service/redirect.service";
import {map} from "rxjs";
import {take} from "rxjs/operators";
import { Observable } from 'rxjs';


export const adminGuard: CanActivateFn = (route, state): Observable<boolean | UrlTree> => {
  const router = inject(Router);
  const authenticationService = inject(AuthenticationService);
  const redirectService = inject(RedirectService);

  return authenticationService.currentUser$.pipe(
    take(1), // Take only the first value and complete
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
