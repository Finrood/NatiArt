import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting, TestRequest} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {fakeAsync, tick} from '@angular/core/testing';

import {AuthenticationService} from './authentication.service';
import {TokenService} from './token.service';
import {RoleName, User} from '../models/user.model';
import {environment} from '../../../environments/environment';

describe('authenticationService', () => {
  const REFRESH_URL: string =
    `${environment.api.directory.url}${environment.api.directory.endpoints.refreshToken}`;
  const CURRENT_USER_URL: string =
    `${environment.api.directory.url}${environment.api.directory.endpoints.user}${environment.api.directory.endpoints.current}`;

  const mockUser: User = {
    id: 'user-1',
    username: 'user@natiart.test',
    profile: null as unknown as User['profile'],
    role: RoleName.USER,
    externalId: 'ext-1',
  };

  function unsignedToken(expSeconds: number): string {
    const payload: string = btoa(JSON.stringify({exp: expSeconds}))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    return `header.${payload}.signature`;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should be created', () => {
    const service: AuthenticationService = TestBed.inject(AuthenticationService);
    expect(service).toBeTruthy();
    TestBed.inject(HttpTestingController).verify();
    service.ngOnDestroy();
  });

  it('resets the user principal when tokens are cleared outside the service (interceptor wipe)', fakeAsync(() => {
    // No stored tokens: constructor init resolves without egress.
    const service: AuthenticationService = TestBed.inject(AuthenticationService);
    tick();
    const httpTesting: HttpTestingController = TestBed.inject(HttpTestingController);
    httpTesting.verify();
    const tokenService: TokenService = TestBed.inject(TokenService);

    // Simulate a logged-in session established before the wipe.
    tokenService.accessToken = unsignedToken(Math.floor(Date.now() / 1000) + 3600);
    (service as unknown as { updateStateForTest: void }).updateStateForTest;
    service.fetchCurrentUser().subscribe({error: () => undefined});
    const userReq: TestRequest = httpTesting.expectOne(CURRENT_USER_URL);
    userReq.flush(mockUser);
    tick();
    let current: User | null | undefined;
    service.currentUser$.subscribe((user: User | null) => (current = user)).unsubscribe();
    expect(current).toEqual(mockUser);

    // The interceptor path calls TokenService.clearTokens() directly; the
    // service must observe the wipe and drop the stale principal so guards
    // deny instead of activating on a dead session.
    tokenService.clearTokens();
    tick();

    let afterWipe: User | null | undefined = mockUser;
    service.currentUser$.subscribe((user: User | null) => (afterWipe = user)).unsubscribe();
    expect(afterWipe).toBeNull();
    let loggedIn = true;
    service.isLoggedIn$.subscribe((value: boolean) => (loggedIn = value)).unsubscribe();
    expect(loggedIn).toBeFalse();
    expect(service.isAdmin).toBeFalse();
    httpTesting.verify();
    service.ngOnDestroy();
  }));

  it('navigates to login exactly once when the user fetch is rejected (no double reset)', fakeAsync(() => {
    const future: number = Math.floor(Date.now() / 1000) + 3600;
    localStorage.setItem('accessToken', unsignedToken(future));
    localStorage.setItem('refreshToken', unsignedToken(future));

    const service: AuthenticationService = TestBed.inject(AuthenticationService);
    const router: Router = TestBed.inject(Router);
    const navigateSpy: jasmine.Spy = spyOn(router, 'navigate').and.resolveTo(true);
    const httpTesting: HttpTestingController = TestBed.inject(HttpTestingController);

    // Constructor init fetches the current user; reject it once.
    const initReq: TestRequest = httpTesting.expectOne(CURRENT_USER_URL);
    initReq.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();

    // One 401 must clear once and navigate once — the fetch catchError must
    // not route through handleError's second reset.
    const tokenService: TokenService = TestBed.inject(TokenService);
    expect(tokenService.accessToken).toBeNull();
    expect(navigateSpy).toHaveBeenCalledTimes(1);
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
    httpTesting.verify();
    service.ngOnDestroy();
  }));
});
