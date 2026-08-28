import {TestBed} from '@angular/core/testing';
import {BehaviorSubject, firstValueFrom, Observable} from 'rxjs';
import {ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree} from '@angular/router';

import {authGuard} from './auth.guard';
import {AuthenticationService} from '../service/authentication.service';
import {RedirectService} from '../service/redirect.service';
import {User} from '../models/user.model';

describe('authGuard', () => {
  let authResolved$: BehaviorSubject<boolean>;
  let currentUser$: BehaviorSubject<User | null>;
  const route = {} as ActivatedRouteSnapshot;
  const state = {url: '/dashboard'} as RouterStateSnapshot;

  beforeEach(() => {
    authResolved$ = new BehaviorSubject<boolean>(false);
    currentUser$ = new BehaviorSubject<User | null>(null);
    const loginTree = new UrlTree();

    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthenticationService,
          useValue: {authResolved$, currentUser$, isAdmin: false},
        },
        {
          provide: RedirectService,
          useValue: {
            setRedirectUrl: jasmine.createSpy('setRedirectUrl'),
            getLoginTree: jasmine.createSpy('getLoginTree').and.returnValue(loginTree),
            getDashboardTree: jasmine.createSpy('getDashboardTree').and.returnValue(new UrlTree()),
          },
        },
      ],
    });
  });

  function runGuard(): Observable<boolean | UrlTree> {
    return TestBed.runInInjectionContext(() => authGuard(route, state)) as Observable<boolean | UrlTree>;
  }

  it('must not decide before the auth bootstrap resolves (cold-boot redirect regression)', async () => {
    let decisions = 0;
    const sub = runGuard().subscribe(() => decisions++);
    // authResolved$ is still `false` (initial value of the BehaviorSubject) and no user has
    // been restored yet: the guard must stay silent instead of bouncing to /login.
    expect(decisions).toBe(0);
    sub.unsubscribe();
  });

  it('allows navigation once resolved and a user is present', async () => {
    const decision = runGuard();
    currentUser$.next({username: 'user@example.com'} as User);
    authResolved$.next(true);
    const result = await firstValueFrom(decision);
    expect(result).toBeTrue();
  });

  it('redirects to login once resolved without a user', async () => {
    const decision = runGuard();
    authResolved$.next(true);
    const result = await firstValueFrom(decision);
    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(RedirectService).setRedirectUrl).toHaveBeenCalledWith('/dashboard');
  });
});
