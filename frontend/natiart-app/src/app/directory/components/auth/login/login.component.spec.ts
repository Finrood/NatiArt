import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { ComponentFixture, fakeAsync, tick } from '@angular/core/testing';

import { LoginComponent } from './login.component';
import { AuthenticationService } from '../../../service/authentication.service';
import { TokenService } from '../../../service/token.service';
import { RoleName, User } from '../../../models/user.model';
import { environment } from '../../../../../environments/environment';

describe('LoginComponent', () => {
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

  function setup(): {
    fixture: ComponentFixture<LoginComponent>;
    httpTesting: HttpTestingController;
    tokenService: TokenService;
    navigateSpy: jasmine.Spy;
  } {
    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
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
});
