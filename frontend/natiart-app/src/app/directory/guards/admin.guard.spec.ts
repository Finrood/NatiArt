import {TestBed} from '@angular/core/testing';
import {BehaviorSubject, firstValueFrom, Observable} from 'rxjs';
import {ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree} from '@angular/router';

import {adminGuard} from './admin.guard';
import {AuthenticationService} from '../service/authentication.service';
import {RedirectService} from '../service/redirect.service';
import {User} from '../models/user.model';

describe('adminGuard', () => {
  let authResolved$: BehaviorSubject<boolean>;
  let currentUser$: BehaviorSubject<User | null>;
  let isAdmin: boolean;
  const route = {} as ActivatedRouteSnapshot;
  const state = {url: '/admin'} as RouterStateSnapshot;

  beforeEach(() => {
    authResolved$ = new BehaviorSubject<boolean>(false);
    currentUser$ = new BehaviorSubject<User | null>(null);
    isAdmin = false;

    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthenticationService,
          useValue: {authResolved$, currentUser$, get isAdmin() { return isAdmin; }},
        },
        {
          provide: RedirectService,
          useValue: {
            setRedirectUrl: jasmine.createSpy('setRedirectUrl'),
            getLoginTree: jasmine.createSpy('getLoginTree').and.returnValue(new UrlTree()),
            getDashboardTree: jasmine.createSpy('getDashboardTree').and.returnValue(new UrlTree()),
          },
        },
      ],
    });
  });

  function runGuard(): Observable<boolean | UrlTree> {
    return TestBed.runInInjectionContext(() => adminGuard(route, state)) as Observable<boolean | UrlTree>;
  }

  it('must not decide before the auth bootstrap resolves', async () => {
    let decisions = 0;
    const sub = runGuard().subscribe(() => decisions++);
    expect(decisions).toBe(0);
    sub.unsubscribe();
  });

  it('allows navigation for an admin once resolved', async () => {
    const decision = runGuard();
    isAdmin = true;
    currentUser$.next({username: 'admin@example.com'} as User);
    authResolved$.next(true);
    const result = await firstValueFrom(decision);
    expect(result).toBeTrue();
  });

  it('redirects a non-admin user to the dashboard once resolved', async () => {
    const decision = runGuard();
    currentUser$.next({username: 'user@example.com'} as User);
    authResolved$.next(true);
    const result = await firstValueFrom(decision);
    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(RedirectService).getDashboardTree).toHaveBeenCalled();
  });

  it('redirects an anonymous visitor to login once resolved', async () => {
    const decision = runGuard();
    authResolved$.next(true);
    const result = await firstValueFrom(decision);
    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(RedirectService).getLoginTree).toHaveBeenCalled();
  });
});
