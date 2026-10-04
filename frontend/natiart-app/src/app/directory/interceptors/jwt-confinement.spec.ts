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

describe('API credential confinement with shared authentication', () => {
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

  for (const configuration of [
    {name: 'relative production', directory: productionEnvironment.api.directory.url, product: productionEnvironment.api.product.url},
    {name: 'absolute production', directory: 'https://shop.example.test/server/directory', product: 'https://shop.example.test/server/product'},
    {name: 'absolute development', directory: originalDirectoryUrl, product: originalProductUrl},
  ]) {
    it(`scopes credentials to ${configuration.name} API bases`, fakeAsync(() => {
      environment.api.directory.url = configuration.directory;
      environment.api.product.url = configuration.product;
      const {http, httpTesting, tokenService} = setup();
      tokenService.accessToken = 'production-access';
      const origin: string = new URL(configuration.directory, window.location.origin).origin;
      const foreignPort: URL = new URL(origin);
      foreignPort.port = foreignPort.port === '444' ? '445' : '444';
      const otherScheme: URL = new URL(origin);
      otherScheme.protocol = otherScheme.protocol === 'https:' ? 'http:' : 'https:';
      const productPath: string = new URL(configuration.product, window.location.origin).pathname;
      const directoryPath: string = new URL(configuration.directory, window.location.origin).pathname;
      const requests: Array<{url: string; authenticated: boolean}> = [
        {url: `${configuration.directory}/current`, authenticated: true},
        {url: `${configuration.product}/products`, authenticated: true},
        {url: `${origin}/account`, authenticated: directoryPath === "/"},
        {url: `${origin}${productPath}ivity/orders`, authenticated: directoryPath === "/"},
        {url: `${origin}${directoryPath}-other/current`, authenticated: directoryPath === "/"},
        {url: `${foreignPort.origin}${productPath}/orders`, authenticated: false},
        {url: `${otherScheme.origin}${productPath}/orders`, authenticated: false},
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

  }

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
    const url: string = `${new URL(productionEnvironment.api.product.url, window.location.origin).origin}/account`;
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
