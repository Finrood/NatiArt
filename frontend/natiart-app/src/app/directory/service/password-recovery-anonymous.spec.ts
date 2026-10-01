import {fakeAsync, TestBed, tick} from '@angular/core/testing';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {Router} from '@angular/router';
import {PasswordResetService} from './password-reset.service';
import {AuthenticationService} from './authentication.service';
import {TokenService} from './token.service';
import {jwtInterceptor} from '../interceptors/jwt-interceptor.service';
import {environment} from '../../../environments/environment';

describe('Anonymous recovery with stale browser session', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({providers: [provideHttpClient(withInterceptors([jwtInterceptor])), provideHttpClientTesting(),
      {provide: Router, useValue: {navigate: jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true))}}]});
  });
  afterEach(() => { TestBed.inject(AuthenticationService).ngOnDestroy(); localStorage.clear(); TestBed.inject(HttpTestingController).verify(); });

  for (const operation of ['request', 'redemption']) {
    it('does not send existing access credentials on ' + operation, fakeAsync(() => {
      TestBed.inject(AuthenticationService);
      tick();
      (TestBed.inject(Router).navigate as jasmine.Spy).calls.reset();
      const tokens = TestBed.inject(TokenService);
      tokens.accessToken = 'revoked-access';
      tokens.refreshToken = 'revoked-refresh';
      const service = TestBed.inject(PasswordResetService);
      if (operation === 'request') {
        service.requestReset('customer@example.test').subscribe({error: () => {}});
      } else {
        service.resetPassword('12345678-1234-1234-1234-123456789abc', 'NewPass123', 'NewPass123').subscribe({error: () => {}});
      }
      const suffix = operation === 'request' ? environment.api.directory.endpoints.passwordResetRequest : environment.api.directory.endpoints.passwordReset;
      const request = TestBed.inject(HttpTestingController).expectOne(environment.api.directory.url + suffix);
      expect(request.request.headers.has('Authorization')).toBeFalse();
      if (operation === 'request') request.flush({message: 'If the account exists, recovery instructions will be sent.'}, {status: 202, statusText: 'Accepted'});
      else request.flush(null, {status: 204, statusText: 'No Content'});
      TestBed.inject(HttpTestingController).expectNone(environment.api.directory.url + environment.api.directory.endpoints.refreshToken);
      expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
      tick();
    }));
  }
  for (const path of ['/forgot-password', '/reset-password']) {
    it('keeps recovery usable during rejected stored-session bootstrap on ' + path, fakeAsync(() => {
      const previous = window.location.pathname;
      window.history.pushState(null, '', path);
      try {
        const jwt = 'stale.' + btoa(JSON.stringify({exp: Math.floor(Date.now() / 1000) + 3600})) + '.signature';
        const tokens = TestBed.inject(TokenService);
        tokens.accessToken = jwt;
        tokens.refreshToken = jwt;
        TestBed.inject(AuthenticationService);
        tick();
        const http = TestBed.inject(HttpTestingController);
        http.expectOne(environment.api.directory.url + environment.api.directory.endpoints.user + environment.api.directory.endpoints.current)
          .flush('', {status: 401, statusText: 'Revoked session'});
        tick();
        TestBed.inject(PasswordResetService).requestReset('buyer@example.test').subscribe();
        const request = http.expectOne(environment.api.directory.url + environment.api.directory.endpoints.passwordResetRequest);
        expect(request.request.headers.has('Authorization')).toBeFalse();
        request.flush({message: 'If the account exists, recovery instructions will be sent.'}, {status: 202, statusText: 'Accepted'});
        http.expectNone(environment.api.directory.url + environment.api.directory.endpoints.refreshToken);
        expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
      } finally {
        window.history.pushState(null, '', previous);
      }
    }));
  }
});
