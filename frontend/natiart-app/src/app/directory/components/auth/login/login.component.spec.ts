import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { ComponentFixture, fakeAsync, tick } from '@angular/core/testing';

import { LoginComponent } from './login.component';
import { AuthenticationService } from '../../../service/authentication.service';
import { TokenService } from '../../../service/token.service';
import { RoleName, User } from '../../../models/user.model';
import { environment } from '../../../../../environments/environment';

describe('LoginComponent', () => {
  const CURRENT_USER_URL: string =
    `${environment.api.directory.url}${environment.api.directory.endpoints.user}${environment.api.directory.endpoints.current}`;
  const LOGIN_URL: string =
    `${environment.api.directory.url}${environment.api.directory.endpoints.login}`;

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

  function setup(): {
    fixture: ComponentFixture<LoginComponent>;
    httpTesting: HttpTestingController;
    tokenService: TokenService;
    navigateSpy: jasmine.Spy;
  } {
    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const httpTesting: HttpTestingController = TestBed.inject(HttpTestingController);
    const tokenService: TokenService = TestBed.inject(TokenService);
    const router: Router = TestBed.inject(Router);
    const navigateSpy: jasmine.Spy = spyOn(router, 'navigate').and.resolveTo(true);
    const fixture: ComponentFixture<LoginComponent> = TestBed.createComponent(LoginComponent);
    return {fixture, httpTesting, tokenService, navigateSpy};
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should create', () => {
    const {fixture, httpTesting} = setup();
    expect(fixture.componentInstance).toBeTruthy();
    fixture.detectChanges();
    httpTesting.verify();
    TestBed.inject(AuthenticationService).ngOnDestroy();
  });

  it('redirects to the dashboard only after the stored token validates', fakeAsync(() => {
    const {fixture, httpTesting, tokenService, navigateSpy} = setup();
    // The service constructor already ran (with no tokens); isolate ngOnInit behavior.
    navigateSpy.calls.reset();
    tokenService.accessToken = unsignedToken(Math.floor(Date.now() / 1000) + 3600);

    fixture.detectChanges();
    tick();

    const req: TestRequest = httpTesting.expectOne(CURRENT_USER_URL);
    req.flush(mockUser);
    tick();

    expect(navigateSpy).toHaveBeenCalledWith(['/dashboard']);
    httpTesting.verify();
    TestBed.inject(AuthenticationService).ngOnDestroy();
  }));

  it('stays on login and clears tokens when the stored token is rejected', fakeAsync(() => {
    const {fixture, httpTesting, tokenService, navigateSpy} = setup();
    // The service constructor already ran (with no tokens); isolate ngOnInit behavior.
    navigateSpy.calls.reset();
    tokenService.accessToken = unsignedToken(Math.floor(Date.now() / 1000) + 3600);

    fixture.detectChanges();
    tick();

    const req: TestRequest = httpTesting.expectOne(CURRENT_USER_URL);
    req.flush('', {status: 401, statusText: 'Unauthorized'});
    tick();

    expect(tokenService.accessToken).toBeNull();
    const wentToDashboard: boolean = navigateSpy.calls.allArgs()
      .some((args: unknown[]): boolean => JSON.stringify(args) === JSON.stringify([['/dashboard']]));
    expect(wentToDashboard).toBeFalse();
    httpTesting.verify();
    TestBed.inject(AuthenticationService).ngOnDestroy();
  }));

  it('keeps stored tokens when validation fails transiently (500), staying on login', fakeAsync(() => {
    const {fixture, httpTesting, tokenService, navigateSpy} = setup();
    navigateSpy.calls.reset();
    const stored: string = unsignedToken(Math.floor(Date.now() / 1000) + 3600);
    tokenService.accessToken = stored;

    fixture.detectChanges();
    tick();

    const req: TestRequest = httpTesting.expectOne(CURRENT_USER_URL);
    req.flush('', {status: 500, statusText: 'Server Error'});
    tick();

    expect(tokenService.accessToken).toBe(stored);
    expect(navigateSpy).not.toHaveBeenCalled();
    httpTesting.verify();
    TestBed.inject(AuthenticationService).ngOnDestroy();
  }));

  it('makes no validation request when no token is stored', fakeAsync(() => {
    const {fixture, httpTesting, navigateSpy} = setup();
    // The service constructor already ran (with no tokens); isolate ngOnInit behavior.
    navigateSpy.calls.reset();

    fixture.detectChanges();
    tick();

    httpTesting.expectNone(CURRENT_USER_URL);
    expect(navigateSpy).not.toHaveBeenCalled();
    httpTesting.verify();
    TestBed.inject(AuthenticationService).ngOnDestroy();
  }));

  it('ignores duplicate submissions while login is in flight', fakeAsync(() => {
    const {fixture, httpTesting} = setup();
    const component: LoginComponent = fixture.componentInstance;
    component.loginForm.setValue({credentials: {username: 'user@natiart.test', password: 'password'}});

    component.doLoginUser();
    const loginRequest: TestRequest = httpTesting.expectOne(LOGIN_URL);
    component.doLoginUser();
    expect(httpTesting.match(LOGIN_URL)).toHaveSize(0);
    expect(component.isSubmitting).toBeTrue();

    const accessExpiration: number = Math.floor(Date.now() / 1000) + 3600;
    const refreshExpiration: number = Math.floor(Date.now() / 1000) + 8 * 24 * 3600;
    loginRequest.flush({
      accessToken: unsignedToken(accessExpiration),
      refreshToken: unsignedToken(refreshExpiration),
    });
    const currentUserRequest: TestRequest = httpTesting.expectOne(CURRENT_USER_URL);
    currentUserRequest.flush(mockUser);
    tick();

    expect(component.isSubmitting).toBeFalse();
    httpTesting.verify();
    TestBed.inject(AuthenticationService).ngOnDestroy();
  }));
});
