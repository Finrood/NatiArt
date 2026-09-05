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

  it('refreshes an expired session through the env-configured endpoint', fakeAsync(() => {
    const past: number = Math.floor(Date.now() / 1000) - 60;
    // Beyond both refresh buffers (5 min access, 1 day refresh) so the
    // token monitor stays quiet after the refresh completes.
    const future: number = Math.floor(Date.now() / 1000) + 8 * 24 * 3600;
    localStorage.setItem('accessToken', unsignedToken(past));
    localStorage.setItem('refreshToken', unsignedToken(future));

    // Constructor init picks up the stored tokens and refreshes synchronously.
    const service: AuthenticationService = TestBed.inject(AuthenticationService);

    const refreshReq: TestRequest = TestBed.inject(HttpTestingController).expectOne(REFRESH_URL);
    expect(refreshReq.request.headers.get('Authorization')).toBe(`Bearer ${unsignedToken(future)}`);

    const newAccess: string = unsignedToken(future);
    refreshReq.flush({accessToken: newAccess, refreshToken: unsignedToken(future)});

    const userReq: TestRequest = TestBed.inject(HttpTestingController).expectOne(CURRENT_USER_URL);
    expect(userReq.request.headers.get('Authorization')).toBe(`Bearer ${newAccess}`);
    userReq.flush(mockUser);

    // Let the token monitor run once: with fresh tokens it must stay quiet.
    tick();

    const tokenService: TokenService = TestBed.inject(TokenService);
    expect(tokenService.accessToken).toBe(newAccess);
    TestBed.inject(HttpTestingController).verify();
    service.ngOnDestroy();
  }));
});
