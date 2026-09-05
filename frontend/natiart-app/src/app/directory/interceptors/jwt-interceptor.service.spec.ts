import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {Router} from '@angular/router';
import {fakeAsync, flush, tick} from '@angular/core/testing';

import {jwtInterceptor} from './jwt-interceptor.service';
import {TokenService} from '../service/token.service';
import {environment} from '../../../environments/environment';

describe('jwtInterceptor', () => {
  const REFRESH_URL = `${environment.api.directory.url}${environment.api.directory.endpoints.refreshToken}`;

  function setup() {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        {provide: Router, useValue: {navigate: jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true))}},
      ],
    });
    const http = TestBed.inject(HttpClient);
    const httpTesting = TestBed.inject(HttpTestingController);
    const tokenService = TestBed.inject(TokenService);
    return {http, httpTesting, tokenService};
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('attaches the bearer token to API requests', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'abc';

    http.get('/products').subscribe(() => {
    });
    const req = httpTesting.expectOne('/products');
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc');
    req.flush({});
    httpTesting.verify();
  }));

  it('never sends the Authorization header to auth/refresh endpoints', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'abc';

    http.post('/login', {}).subscribe(() => {
    });
    http.post('/register-user', {}).subscribe(() => {
    });
    http.post(REFRESH_URL, null).subscribe(() => {
    });

    const login = httpTesting.expectOne('/login');
    const register = httpTesting.expectOne('/register-user');
    const refresh = httpTesting.expectOne(REFRESH_URL);
    expect(login.request.headers.has('Authorization')).toBeFalse();
    expect(register.request.headers.has('Authorization')).toBeFalse();
    expect(refresh.request.headers.has('Authorization')).toBeFalse();
    login.flush({});
    register.flush({});
    refresh.flush({});
    httpTesting.verify();
  }));

  it('never sends the Authorization header to excluded third-party domains', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'abc';

    http.get('https://viacep.com.br/ws/01001000/json').subscribe(() => {
    });
    const req = httpTesting.expectOne('https://viacep.com.br/ws/01001000/json');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
    httpTesting.verify();
  }));

  it('excludes the configured ViaCEP host even when the env URL is overridden', fakeAsync(() => {
    const original: string = environment.api.viaCep.url;
    environment.api.viaCep.url = 'https://zipmirror.test/lookup';
    try {
      const {http, httpTesting, tokenService} = setup();
      tokenService.accessToken = 'abc';

      http.get('https://zipmirror.test/lookup/01001000/json').subscribe(() => {
      });
      const req = httpTesting.expectOne('https://zipmirror.test/lookup/01001000/json');
      expect(req.request.headers.has('Authorization')).toBeFalse();
      req.flush({});
      httpTesting.verify();
    } finally {
      environment.api.viaCep.url = original;
    }
  }));

  it('when logged out it sends no Authorization header at all (no "Bearer null")', fakeAsync(() => {
    const {http, httpTesting} = setup();

    http.get('/cart').subscribe(() => {
    });
    const req = httpTesting.expectOne('/cart');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
    httpTesting.verify();
  }));

  it('performs a single-flight refresh and retries the failed request once', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'old-access';
    tokenService.refreshToken = 'old-refresh';

    let body: any;
    http.get('/api/secure').subscribe({
      next: (r) => (body = r),
      error: () => {
      },
    });

    const first = httpTesting.expectOne('/api/secure');
    expect(first.request.headers.get('Authorization')).toBe('Bearer old-access');
    first.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();

    // The 401 triggers exactly one refresh call, carrying the refresh token (as its body/header).
    const refresh = httpTesting.expectOne(REFRESH_URL);
    expect(refresh.request.headers.get('Authorization')).toBe('Bearer old-refresh');
    refresh.flush({accessToken: 'new-access', refreshToken: 'new-refresh'});
    tick();

    // And the original request is retried with the new bearer + the retry guard header.
    const retried = httpTesting.expectOne('/api/secure');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer new-access');
    // The retried request must NOT re-trigger another refresh if it also fails.
    expect(retried.request.headers.get('X-Auth-Retried')).toBe('1');
    retried.flush({ok: true}, {status: 200, statusText: 'OK'});
    tick();

    httpTesting.verify();
    expect(body).toEqual({ok: true});
  }));

  it('shares one refresh across concurrent 401s (single-flight)', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'old-access';
    tokenService.refreshToken = 'old-refresh';

    http.get('/a').subscribe({next: () => {
    }, error: () => {
    }});
    http.get('/b').subscribe({next: () => {
    }, error: () => {
    }});

    const reqA = httpTesting.expectOne('/a');
    const reqB = httpTesting.expectOne('/b');
    reqA.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();
    reqB.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();

    // Only ONE refresh request for both 401s.
    const refresh = httpTesting.expectOne(REFRESH_URL);
    refresh.flush({accessToken: 'new-access', refreshToken: 'new-refresh'});
    tick();

    httpTesting.expectOne('/a').flush({});
    httpTesting.expectOne('/b').flush({});
    tick();
    httpTesting.verify();
  }));

  it('does not refresh again when the retried request itself returns 401', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'old-access';
    tokenService.refreshToken = 'old-refresh';

    let error: any;
    http.get('/api/secure').subscribe({error: (e) => (error = e)});

    const first = httpTesting.expectOne('/api/secure');
    first.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();
    const refresh = httpTesting.expectOne(REFRESH_URL);
    refresh.flush({accessToken: 'new-access', refreshToken: 'new-refresh'});
    tick();
    const retried = httpTesting.expectOne('/api/secure');
    expect(retried.request.headers.get('X-Auth-Retried')).toBe('1');
    retried.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();

    // No second refresh attempt: the guard header prevented it.
    httpTesting.expectNone(REFRESH_URL);
    httpTesting.verify();
    expect(error).toBeTruthy();
  }));
});
