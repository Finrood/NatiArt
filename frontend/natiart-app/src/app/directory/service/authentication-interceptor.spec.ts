import {fakeAsync, TestBed, tick} from '@angular/core/testing';
import {HttpClient, provideHttpClient, withInterceptors} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {Router} from '@angular/router';
import {AuthenticationService} from './authentication.service';
import {TokenService} from './token.service';
import {jwtInterceptor} from '../interceptors/jwt-interceptor.service';
import {environment} from '../../../environments/environment';
import {RoleName, User} from '../models/user.model';

describe('Authentication interceptor rejection policy', () => {
  for (const status of [401, 403, 503]) {
    it(`handles API 401 followed by refresh ${status} according to session policy`, fakeAsync(() => {
      localStorage.clear();
      const navigate = jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true));
      TestBed.configureTestingModule({providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])), provideHttpClientTesting(),
        {provide: Router, useValue: {navigate}}
      ]});
      const service = TestBed.inject(AuthenticationService);
      const http = TestBed.inject(HttpTestingController);
      const client = TestBed.inject(HttpClient);
      const tokens = TestBed.inject(TokenService);
      tick();
      const jwt = (name: string): string => name + '.' + btoa(JSON.stringify({exp: Math.floor(Date.now() / 1000) + 8 * 86400})) + '.s';
      const access = jwt('access');
      const refresh = jwt('refresh');
      tokens.accessToken = access;
      tokens.refreshToken = refresh;
      let current: User | null = null;
      const userSubscription = service.currentUser$.subscribe(value => current = value);
      service.fetchCurrentUser().subscribe();
      http.expectOne(environment.api.directory.url + environment.api.directory.endpoints.user + environment.api.directory.endpoints.current)
        .flush({id: 'A', username: 'A@example.test', role: RoleName.USER});
      let responseStatus: number | undefined;
      const apiUrl = environment.api.product.url + '/independent-session-probe';
      client.get(apiUrl).subscribe({error: error => responseStatus = error.status});
      const initial = http.expectOne(apiUrl);
      expect(initial.request.headers.get('Authorization')).toBe('Bearer ' + access);
      initial.flush('', {status: 401, statusText: 'Unauthorized'});
      const refreshRequest = http.expectOne(environment.api.directory.url + environment.api.directory.endpoints.refreshToken);
      expect(refreshRequest.request.headers.get('Authorization')).toBe('Bearer ' + refresh);
      refreshRequest.flush('', {status, statusText: 'Probe rejection'});
      expect(responseStatus).toBe(status);
      if (status === 503) {
        expect(tokens.accessToken).toBe(access);
        expect(tokens.refreshToken).toBe(refresh);
        expect(current).not.toBeNull();
        expect(navigate).not.toHaveBeenCalled();
      } else {
        expect(tokens.accessToken).toBeNull();
        expect(tokens.refreshToken).toBeNull();
        expect(current).toBeNull();
        expect(navigate).toHaveBeenCalledWith(['/login']);
      }
      expect(http.match(apiUrl).length).toBe(0);
      userSubscription.unsubscribe();
      service.ngOnDestroy();
      localStorage.clear();
      http.verify();
    }));
  }
});
