import {fakeAsync, TestBed, tick} from '@angular/core/testing';
import {HttpClient, provideHttpClient, withInterceptors} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {Router} from '@angular/router';
import {AuthenticationService} from './authentication.service';
import {TokenService} from './token.service';
import {jwtInterceptor} from '../interceptors/jwt-interceptor.service';
import {environment} from '../../../environments/environment';
import {RoleName, User} from '../models/user.model';

describe('Authentication session generation', () => {
  const base = environment.api.directory.url;
  const refreshUrl = base + environment.api.directory.endpoints.refreshToken;
  const currentUrl = base + environment.api.directory.endpoints.user + environment.api.directory.endpoints.current;
  let service: AuthenticationService;
  let http: HttpTestingController;
  let tokens: TokenService;
  const user = (id: string): User => ({id, username: id + '@example.test', profile: null as unknown as User['profile'], role: RoleName.USER, externalId: id});
  const jwt = (name: string, seconds = 8 * 86400): string => name + '.' + btoa(JSON.stringify({exp: Math.floor(Date.now() / 1000) + seconds})) + '.signature';

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting(),
      {provide: Router, useValue: {navigate: jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true))}}]});
    http = TestBed.inject(HttpTestingController);
    tokens = TestBed.inject(TokenService);
  });
  afterEach(() => { service.ngOnDestroy(); localStorage.clear(); http.verify(); });

  it('does not restore current user after credentials are cleared during a lookup', fakeAsync(() => {
    service = TestBed.inject(AuthenticationService);
    tick();
    tokens.accessToken = jwt('a'); tokens.refreshToken = jwt('r');
    let current: User | null = null;
    const subscription = service.currentUser$.subscribe(value => current = value);
    service.fetchCurrentUser().subscribe({error: () => {}});
    const pending = http.expectOne(currentUrl);
    tokens.clearTokens();
    pending.flush(user('A'));
    expect(current).toBeNull();
    expect(tokens.accessToken).toBeNull();
    subscription.unsubscribe(); service.ngOnDestroy();
  }));

  it('does not replace account B credentials with delayed account A refresh', fakeAsync(() => {
    service = TestBed.inject(AuthenticationService);
    tick();
    tokens.accessToken = jwt('A-access'); tokens.refreshToken = jwt('A-refresh');
    service.refreshAccessToken().subscribe({error: () => {}});
    const pending = http.expectOne(refreshUrl);
    const bAccess = jwt('B-access'); const bRefresh = jwt('B-refresh');
    service.login({username: 'B@example.test', password: 'Password123'}).subscribe();
    http.expectOne(base + environment.api.directory.endpoints.login).flush({accessToken: bAccess, refreshToken: bRefresh});
    http.expectOne(currentUrl).flush(user('B'));
    pending.flush({accessToken: jwt('A-new-access'), refreshToken: jwt('A-new-refresh')});
    expect(tokens.accessToken).toBe(bAccess);
    expect(tokens.refreshToken).toBe(bRefresh);
    service.ngOnDestroy();
  }));

  it('does not clear account B after delayed account A refresh rejection', fakeAsync(() => {
    service = TestBed.inject(AuthenticationService);
    tick();
    tokens.accessToken = jwt('A-access'); tokens.refreshToken = jwt('A-refresh');
    service.refreshAccessToken().subscribe({error: () => {}});
    const pending = http.expectOne(refreshUrl);
    const bAccess = jwt('B-access'); const bRefresh = jwt('B-refresh');
    service.login({username: 'B@example.test', password: 'Password123'}).subscribe();
    http.expectOne(base + environment.api.directory.endpoints.login).flush({accessToken: bAccess, refreshToken: bRefresh});
    http.expectOne(currentUrl).flush(user('B'));
    pending.flush('', {status: 401, statusText: 'Old session rejected'});
    expect(tokens.accessToken).toBe(bAccess);
    expect(tokens.refreshToken).toBe(bRefresh);
    service.ngOnDestroy();
  }));

  it('clears local credentials when a pending logout is cancelled', fakeAsync(() => {
    service = TestBed.inject(AuthenticationService); tick();
    tokens.accessToken = jwt('a'); tokens.refreshToken = jwt('r');
    const subscription = service.logout().subscribe();
    const pending = http.expectOne(base + environment.api.directory.endpoints.logout);
    subscription.unsubscribe();
    expect(pending.cancelled).toBeTrue();
    expect(tokens.accessToken).toBeNull(); expect(tokens.refreshToken).toBeNull();
    service.ngOnDestroy();
  }));

  it('does not clear a newer login when an older logout is cancelled', fakeAsync(() => {
    service = TestBed.inject(AuthenticationService); tick();
    tokens.accessToken = jwt('a'); tokens.refreshToken = jwt('r');
    const subscription = service.logout().subscribe();
    const pending = http.expectOne(base + environment.api.directory.endpoints.logout);
    const bAccess: string = jwt('B-access'); const bRefresh: string = jwt('B-refresh');
    service.login({username: 'B@example.test', password: 'Password123'}).subscribe();
    http.expectOne(base + environment.api.directory.endpoints.login).flush({accessToken: bAccess, refreshToken: bRefresh});
    http.expectOne(currentUrl).flush(user('B'));
    subscription.unsubscribe();
    expect(pending.cancelled).toBeTrue();
    expect(tokens.accessToken).toBe(bAccess); expect(tokens.refreshToken).toBe(bRefresh);
    service.ngOnDestroy();
  }));

  it('bounds an unavailable logout and clears local credentials', fakeAsync(() => {
    service = TestBed.inject(AuthenticationService); tick();
    tokens.accessToken = jwt('a'); tokens.refreshToken = jwt('r');
    let failed: boolean = false;
    service.logout().subscribe({error: (): void => { failed = true; }});
    const pending = http.expectOne(base + environment.api.directory.endpoints.logout);
    tick(10000);
    expect(pending.cancelled).toBeTrue(); expect(failed).toBeTrue();
    expect(tokens.accessToken).toBeNull(); expect(tokens.refreshToken).toBeNull();
    service.ngOnDestroy();
  }));

  it('clears definitively rejected refresh credentials', fakeAsync(() => {
    service = TestBed.inject(AuthenticationService);
    tick();
    tokens.accessToken = jwt('a'); tokens.refreshToken = jwt('r');
    service.refreshAccessToken().subscribe({error: () => {}});
    http.expectOne(refreshUrl).flush('', {status: 401, statusText: 'Unauthorized'});
    expect(tokens.accessToken).toBeNull(); expect(tokens.refreshToken).toBeNull();
    service.ngOnDestroy();
  }));

  it('does not refresh every minute when the server preserves fixed refresh expiry', fakeAsync(() => {
    service = TestBed.inject(AuthenticationService);
    tick();
    const r = jwt('r', 3600);
    tokens.accessToken = jwt('a', 60); tokens.refreshToken = r;
    tick(60000);
    http.expectOne(refreshUrl).flush({accessToken: jwt('new', 7200), refreshToken: r});
    http.expectOne(currentUrl).flush(user('A'));
    tick(60000);
    const requests = http.match(refreshUrl);
    expect(requests.length).toBe(0);
    requests.forEach(request => request.flush({accessToken: jwt('new2', 7200), refreshToken: r}));
    http.match(currentUrl).forEach(request => request.flush(user('A')));
    service.ngOnDestroy();
  }));
});

describe('Authentication production bootstrap', () => {
  it('bootstraps a valid access-only session through the production interceptor', fakeAsync(() => {
    localStorage.clear();
    localStorage.setItem('accessToken', 'a.' + btoa(JSON.stringify({exp: Math.floor(Date.now() / 1000) + 7200})) + '.s');
    TestBed.configureTestingModule({providers: [provideHttpClient(withInterceptors([jwtInterceptor])), provideHttpClientTesting(),
      {provide: Router, useValue: {navigate: jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true))}}]});
    const service = TestBed.inject(AuthenticationService);
    const http = TestBed.inject(HttpTestingController);
    tick();
    const requests = http.match(environment.api.directory.url + environment.api.directory.endpoints.user + environment.api.directory.endpoints.current);
    expect(requests.length).toBe(1);
    requests.forEach(request => request.flush({id: 'A', role: RoleName.USER}));
    tick();
    http.expectNone(environment.api.directory.url + environment.api.directory.endpoints.refreshToken);
    let loaded: User | null = null;
    const subscription = service.currentUser$.subscribe(user => loaded = user);
    expect(loaded).toBeTruthy(); subscription.unsubscribe();
    service.ngOnDestroy(); localStorage.clear(); http.verify();
  }));
});
