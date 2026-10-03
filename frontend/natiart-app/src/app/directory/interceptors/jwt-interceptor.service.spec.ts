import {TestBed} from '@angular/core/testing';
import {HttpClient} from '@angular/common/http';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting, TestRequest} from '@angular/common/http/testing';
import {Router} from '@angular/router';
import {fakeAsync, flush, tick} from '@angular/core/testing';

import {jwtInterceptor} from './jwt-interceptor.service';
import {TokenService} from '../service/token.service';
import {environment} from '../../../environments/environment';
import {environment as productionEnvironment} from '../../../environments/environment.production';

describe('jwtInterceptor', () => {
  const REFRESH_URL = `${environment.api.directory.url}${environment.api.directory.endpoints.refreshToken}`;
  const LOGOUT_URL = `${environment.api.directory.url}${environment.api.directory.endpoints.logout}`;
  const PRODUCT_URL = `${environment.api.product.url}`;

  const originalDirectoryUrl: string = environment.api.directory.url;
  const originalProductUrl: string = environment.api.product.url;

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
    environment.api.directory.url = originalDirectoryUrl;
    environment.api.product.url = originalProductUrl;
    localStorage.clear();
  });

  it('attaches the bearer token to API requests', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'abc';

    http.get(`${PRODUCT_URL}/products`).subscribe(() => {
    });
    const req = httpTesting.expectOne(`${PRODUCT_URL}/products`);
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

  it('keeps the bearer on lookalike URLs that merely contain an endpoint name', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'abc';

    http.get(`${PRODUCT_URL}/api/search?q=login`).subscribe(() => {
    });
    http.get(`${environment.api.directory.url}/api/search?q=refresh-token`).subscribe(() => {
    });

    const relative: TestRequest = httpTesting.expectOne(`${PRODUCT_URL}/api/search?q=login`);
    const absolute: TestRequest = httpTesting.expectOne(`${environment.api.directory.url}/api/search?q=refresh-token`);
    expect(relative.request.headers.get('Authorization')).toBe('Bearer abc');
    expect(absolute.request.headers.get('Authorization')).toBe('Bearer abc');
    relative.flush({});
    absolute.flush({});
    httpTesting.verify();
  }));

  it('never attaches credentials to foreign origins that reuse an endpoint path', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'abc';

    http.get('https://other.test/login').subscribe(() => {
    });
    const req: TestRequest = httpTesting.expectOne('https://other.test/login');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
    httpTesting.verify();
  }));

  it('preserves a caller-supplied Authorization header', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'application-access';

    http.get(`${PRODUCT_URL}/payments/provider`, {
      headers: {Authorization: 'Basic provider-credential'}
    }).subscribe(() => {
    });
    const req = httpTesting.expectOne(`${PRODUCT_URL}/payments/provider`);
    expect(req.request.headers.get('Authorization')).toBe('Basic provider-credential');
    req.flush({});
    httpTesting.verify();
  }));

  it('still exempts genuine endpoints with query strings', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'abc';

    http.post(`${environment.api.directory.url}/login?next=/dashboard`, {}).subscribe(() => {
    });
    const req: TestRequest = httpTesting.expectOne(`${environment.api.directory.url}/login?next=/dashboard`);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
    httpTesting.verify();
  }));

  it('never sends the Authorization header to foreign domains', fakeAsync(() => {
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
    http.get(`${PRODUCT_URL}/api/secure`).subscribe({
      next: (r) => (body = r),
      error: () => {
      },
    });

    const first = httpTesting.expectOne(`${PRODUCT_URL}/api/secure`);
    expect(first.request.headers.get('Authorization')).toBe('Bearer old-access');
    first.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();

    // The 401 triggers exactly one refresh call, carrying the refresh token (as its body/header).
    const refresh = httpTesting.expectOne(REFRESH_URL);
    expect(refresh.request.headers.get('Authorization')).toBe('Bearer old-refresh');
    refresh.flush({accessToken: 'new-access', refreshToken: 'new-refresh'});
    tick();

    // And the original request is retried with the new bearer plus an internal context guard.
    const retried = httpTesting.expectOne(`${PRODUCT_URL}/api/secure`);
    expect(retried.request.headers.get('Authorization')).toBe('Bearer new-access');
    expect(retried.request.headers.has('X-Auth-Retried')).toBeFalse();
    retried.flush({ok: true}, {status: 200, statusText: 'OK'});
    tick();

    httpTesting.verify();
    expect(body).toEqual({ok: true});
  }));

  it('shares one refresh across concurrent 401s (single-flight)', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'old-access';
    tokenService.refreshToken = 'old-refresh';

    http.get(`${PRODUCT_URL}/a`).subscribe({next: () => {
    }, error: () => {
    }});
    http.get(`${PRODUCT_URL}/b`).subscribe({next: () => {
    }, error: () => {
    }});

    const reqA = httpTesting.expectOne(`${PRODUCT_URL}/a`);
    const reqB = httpTesting.expectOne(`${PRODUCT_URL}/b`);
    reqA.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();
    reqB.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();

    // Only ONE refresh request for both 401s.
    const refresh = httpTesting.expectOne(REFRESH_URL);
    expect(refresh.request.headers.get('Authorization')).toBe('Bearer old-refresh');
    refresh.flush({accessToken: 'new-access', refreshToken: 'new-refresh'});
    tick();

    httpTesting.expectOne(`${PRODUCT_URL}/a`).flush({});
    httpTesting.expectOne(`${PRODUCT_URL}/b`).flush({});
    tick();
    httpTesting.verify();
  }));

  it('does not refresh again when the retried request itself returns 401', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'old-access';
    tokenService.refreshToken = 'old-refresh';

    let error: any;
    http.get(`${PRODUCT_URL}/api/secure`).subscribe({error: (e) => (error = e)});

    const first = httpTesting.expectOne(`${PRODUCT_URL}/api/secure`);
    first.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();
    const refresh = httpTesting.expectOne(REFRESH_URL);
    refresh.flush({accessToken: 'new-access', refreshToken: 'new-refresh'});
    tick();
    const retried = httpTesting.expectOne(`${PRODUCT_URL}/api/secure`);
    retried.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();

    // No second refresh attempt: the request context guard prevents it.
    httpTesting.expectNone(REFRESH_URL);
    httpTesting.verify();
    expect(error).toBeTruthy();
  }));

  it('times out a stalled refresh and permits a later refresh attempt', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'old-access';
    tokenService.refreshToken = 'old-refresh';

    let firstError: unknown;
    http.get(`${PRODUCT_URL}/first`).subscribe({error: (error: unknown) => (firstError = error)});
    httpTesting.expectOne(`${PRODUCT_URL}/first`).flush('', {status: 401, statusText: 'Unauthorized'});
    tick();
    httpTesting.expectOne(REFRESH_URL);

    tick(10001);

    expect(firstError).toBeTruthy();
    expect(tokenService.accessToken).toBeNull();
    expect(tokenService.refreshToken).toBeNull();

    tokenService.accessToken = 'second-access';
    tokenService.refreshToken = 'second-refresh';
    let secondBody: unknown;
    http.get(`${PRODUCT_URL}/second`).subscribe({next: (body: unknown) => (secondBody = body)});
    httpTesting.expectOne(`${PRODUCT_URL}/second`).flush('', {status: 401, statusText: 'Unauthorized'});
    tick();

    const secondRefresh = httpTesting.expectOne(REFRESH_URL);
    secondRefresh.flush({accessToken: 'new-access', refreshToken: 'new-refresh'});
    tick();
    httpTesting.expectOne(`${PRODUCT_URL}/second`).flush({ok: true});
    tick();

    expect(secondBody).toEqual({ok: true});
    httpTesting.verify();
  }));

  it('still sends the bearer on logout so the server can end the session', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'abc';

    http.post(LOGOUT_URL, {}).subscribe(() => {
    });
    const req: TestRequest = httpTesting.expectOne(LOGOUT_URL);
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc');
    req.flush({});
    httpTesting.verify();
  }));

  it('does not refresh on logout 401: it clears tokens and goes to login', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'old-access';
    tokenService.refreshToken = 'old-refresh';
    const router: Router = TestBed.inject(Router);

    let error: unknown;
    http.post(LOGOUT_URL, {}).subscribe({error: (e: unknown) => (error = e)});

    const req: TestRequest = httpTesting.expectOne(LOGOUT_URL);
    expect(req.request.headers.get('Authorization')).toBe('Bearer old-access');
    req.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();

    // No refresh attempt: explicit logout never extends the session.
    httpTesting.expectNone(REFRESH_URL);
    expect(tokenService.accessToken).toBeNull();
    expect(tokenService.refreshToken).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
    expect(error).toBeTruthy();
    httpTesting.verify();
  }));

  it('scopes production credentials to the configured API base paths', fakeAsync(() => {
    environment.api.directory.url = productionEnvironment.api.directory.url;
    environment.api.product.url = productionEnvironment.api.product.url;
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'production-access';
    const origin: string = new URL(productionEnvironment.api.directory.url).origin;
    const requests: Array<{url: string; authenticated: boolean}> = [
      {url: `${productionEnvironment.api.directory.url}/current`, authenticated: true},
      {url: `${productionEnvironment.api.product.url}/products`, authenticated: true},
      {url: `${origin}/account`, authenticated: false},
      {url: `${origin}/server/productivity/orders`, authenticated: false},
      {url: `${origin}/server/directory-other/current`, authenticated: false},
      {url: `${origin}:444/server/product/orders`, authenticated: false},
      {url: `http://${new URL(origin).host}/server/product/orders`, authenticated: false},
      {url: `https://other.test/server/product/orders`, authenticated: false},
      {url: `https://evil${new URL(origin).hostname}/server/product/orders`, authenticated: false},
    ];

    for (const {url, authenticated} of requests) {
      http.get(url).subscribe(() => {});
      const req: TestRequest = httpTesting.expectOne(url);
      expect(req.request.headers.get('Authorization')).withContext(url)
        .toBe(authenticated ? 'Bearer production-access' : null);
      req.flush({});
    }
    httpTesting.verify();
  }));

  it('exempts production directory auth and refresh endpoints within the API base path', fakeAsync(() => {
    environment.api.directory.url = productionEnvironment.api.directory.url;
    environment.api.product.url = productionEnvironment.api.product.url;
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'production-access';
    tokenService.refreshToken = 'production-refresh';
    const directory: string = productionEnvironment.api.directory.url;
    for (const endpoint of ['/login', '/register-user', '/refresh-token']) {
      const url: string = `${directory}${endpoint}`;
      http.post(url, {}).subscribe({error: () => {}});
      const req: TestRequest = httpTesting.expectOne(url);
      expect(req.request.headers.has('Authorization')).withContext(url).toBeFalse();
      req.flush('', {status: 401, statusText: 'Unauthorized'});
    }
    const router: Router = TestBed.inject(Router);
    expect(router.navigate).not.toHaveBeenCalled();
    expect(tokenService.accessToken).toBe('production-access');
    expect(tokenService.refreshToken).toBe('production-refresh');
    httpTesting.verify();
  }));

  it('accepts relative requests only under same-origin configured API paths', fakeAsync(() => {
    environment.api.directory.url = `${window.location.origin}/server/directory`;
    environment.api.product.url = `${window.location.origin}/server/product`;
    const {http, httpTesting, tokenService} = setup();
    tokenService.accessToken = 'relative-access';
    const requests: Array<{url: string; authenticated: boolean}> = [
      {url: '/server/directory/current', authenticated: true},
      {url: `//${window.location.host}/server/product/orders`, authenticated: true},
      {url: '/server/product/orders', authenticated: true},
      {url: '/server/productivity/orders', authenticated: false},
      {url: '/login', authenticated: false},
    ];
    for (const {url, authenticated} of requests) {
      http.get(url).subscribe(() => {});
      const req: TestRequest = httpTesting.expectOne(url);
      expect(req.request.headers.get('Authorization')).withContext(url)
        .toBe(authenticated ? 'Bearer relative-access' : null);
      req.flush({});
    }
    httpTesting.verify();
  }));

  it('does not refresh, mutate tokens or navigate after a foreign 401', fakeAsync(() => {
    const {http, httpTesting, tokenService} = setup();
    const router: Router = TestBed.inject(Router);
    tokenService.accessToken = 'old-access';
    tokenService.refreshToken = 'old-refresh';
    let receivedError: unknown;
    const url: string = 'https://other.test/server/product/orders';
    http.get(url).subscribe({error: (error: unknown) => (receivedError = error)});
    const req: TestRequest = httpTesting.expectOne(url);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();
    expect(receivedError).toBeTruthy();
    expect(tokenService.accessToken).toBe('old-access');
    expect(tokenService.refreshToken).toBe('old-refresh');
    expect(router.navigate).not.toHaveBeenCalled();
    httpTesting.expectNone(REFRESH_URL);
    httpTesting.verify();
  }));

  it('does not refresh or navigate after a non-API path on the production origin returns 401', fakeAsync(() => {
    environment.api.directory.url = productionEnvironment.api.directory.url;
    environment.api.product.url = productionEnvironment.api.product.url;
    const {http, httpTesting, tokenService} = setup();
    const router: Router = TestBed.inject(Router);
    tokenService.accessToken = 'old-access';
    tokenService.refreshToken = 'old-refresh';
    const url: string = `${new URL(productionEnvironment.api.product.url).origin}/account`;
    let receivedError: unknown;
    http.get(url).subscribe({error: (error: unknown) => (receivedError = error)});
    const req: TestRequest = httpTesting.expectOne(url);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();
    expect(receivedError).toBeTruthy();
    expect(tokenService.accessToken).toBe('old-access');
    expect(tokenService.refreshToken).toBe('old-refresh');
    expect(router.navigate).not.toHaveBeenCalled();
    httpTesting.expectNone(`${productionEnvironment.api.directory.url}/refresh-token`);
    httpTesting.verify();
  }));
});
