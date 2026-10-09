import {Component} from '@angular/core';
import {ComponentFixture, fakeAsync, flushMicrotasks, TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting, TestRequest} from '@angular/common/http/testing';
import {provideRouter, Router, RouterOutlet} from '@angular/router';
import {PasswordResetComponent} from './password-reset.component';
import {PasswordResetRequestComponent} from '../password-reset-request/password-reset-request.component';
import {AuthenticationService} from '../../../service/authentication.service';

@Component({imports: [RouterOutlet], template: '<router-outlet />'})
class RecoveryHostComponent {}
@Component({template: '<h1>Sign in with your new password</h1>'})
class LoginDestinationComponent {}

describe('Rendered password recovery HTTP contract', (): void => {
  let fixture: ComponentFixture<RecoveryHostComponent>;
  let http: HttpTestingController;
  let resetAuth: jasmine.Spy;
  const token: string = '12345678-1234-1234-1234-123456789abc';
  const message: string = 'If this address belongs to an account, check your email for recovery instructions.';

  beforeEach(async (): Promise<void> => {
    resetAuth = jasmine.createSpy('resetAuthStateAndRedirect');
    await TestBed.configureTestingModule({imports: [RecoveryHostComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(),
      provideRouter([{path: 'forgot-password', component: PasswordResetRequestComponent},
        {path: 'reset-password', component: PasswordResetComponent},
        {path: 'login', component: LoginDestinationComponent}]),
      {provide: AuthenticationService, useValue: {resetAuthStateAndRedirect: resetAuth}},
    ]}).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RecoveryHostComponent);
  });
  afterEach((): void => { fixture.destroy(); http.verify(); });

  function open(url: string): void {
    void TestBed.inject(Router).navigateByUrl(url); flushMicrotasks(); fixture.detectChanges();
    flushMicrotasks(); fixture.detectChanges();
  }
  function input(name: string, value: string): void {
    const control: HTMLInputElement = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(`input[formControlName="${name}"]`)!;
    control.value = value; control.dispatchEvent(new Event('input')); control.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }
  function button(): HTMLButtonElement {
    return (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button[type="submit"]')!;
  }
  function submit(): void { expect(button().disabled).toBeFalse(); button().click(); fixture.detectChanges(); }
  function request(suffix: string): TestRequest {
    return http.expectOne((candidate): boolean => candidate.url.endsWith(suffix));
  }

  it('submits through the native form once, shows pending state and renders the uniform response', fakeAsync((): void => {
    open('/forgot-password'); input('username', 'customer@example.test'); submit();
    expect(button().disabled).toBeTrue(); button().click();
    const pending: TestRequest = request('/password-reset/request');
    expect(pending.request.body).toEqual({username: 'customer@example.test'});
    pending.flush({message}); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="status"]')?.textContent).toContain(message);
    expect(button().disabled).toBeFalse();
  }));

  it('shows a request failure and permits a native retry', fakeAsync((): void => {
    open('/forgot-password'); input('username', 'customer@example.test'); submit();
    request('/password-reset/request').flush({}, {status: 503, statusText: 'Unavailable'});
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent).toContain('try again');
    submit(); request('/password-reset/request').flush({message}); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).toBeNull();
  }));

  it('cancels an in-flight request when the buyer leaves the page', fakeAsync((): void => {
    open('/forgot-password'); input('username', 'customer@example.test'); submit();
    const pending: TestRequest = request('/password-reset/request');
    fixture.destroy(); expect(pending.cancelled).toBeTrue();
  }));

  it('removes the fragment, checks both passwords, sends the token only in the POST body and clears auth on success', fakeAsync((): void => {
    open('/reset-password#token=' + token);
    expect(TestBed.inject(Router).url).toBe('/reset-password');
    input('password', 'NewPass123'); input('passwordConfirmation', 'Different123');
    expect(button().disabled).toBeTrue();
    input('passwordConfirmation', 'NewPass123'); submit(); expect(button().disabled).toBeTrue();
    const pending: TestRequest = request('/password-reset');
    expect(pending.request.method).toBe('POST'); expect(pending.request.url).not.toContain(token);
    expect(pending.request.body).toEqual({token, password: 'NewPass123', passwordConfirmation: 'NewPass123'});
    pending.flush(null, {status: 204, statusText: 'No Content'});
    flushMicrotasks(); fixture.detectChanges();
    expect(resetAuth).toHaveBeenCalledTimes(1);
    expect(TestBed.inject(Router).url).toBe('/login');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('new password');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain(token);
  }));

  it('renders expired-link guidance and rejects over-limit UTF-8 passwords before HTTP', fakeAsync((): void => {
    open('/reset-password#token=' + token);
    const overlong: string = 'Ab1' + 'é'.repeat(35);
    input('password', overlong); input('passwordConfirmation', overlong); expect(button().disabled).toBeTrue();
    http.expectNone((candidate): boolean => candidate.url.endsWith('/password-reset'));
    input('password', 'NewPass123'); input('passwordConfirmation', 'NewPass123'); submit();
    request('/password-reset').flush({}, {status: 400, statusText: 'Bad Request'});
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent).toContain('invalid or expired');
    expect(resetAuth).not.toHaveBeenCalled();
  }));

  it('shows malformed-link guidance immediately without asking for a password or sending HTTP', fakeAsync((): void => {
    open('/reset-password#token=%25ZZ');
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent).toContain('missing or invalid');
    expect((fixture.nativeElement as HTMLElement).querySelector('input')).toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('a')?.getAttribute('href')).toBe('/forgot-password');
    expect(TestBed.inject(Router).url).toBe('/reset-password');
    http.expectNone((candidate): boolean => candidate.url.endsWith('/password-reset'));
  }));

  it('rejects an absent or duplicated token before showing the password form', fakeAsync((): void => {
    for (const url of ['/reset-password', '/reset-password#token=' + token + '&token=' + token]) {
      open(url);
      expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent).toContain('missing or invalid');
      expect((fixture.nativeElement as HTMLElement).querySelector('input')).toBeNull();
      void TestBed.inject(Router).navigateByUrl('/forgot-password'); flushMicrotasks(); fixture.detectChanges();
    }
    http.expectNone((candidate): boolean => candidate.url.endsWith('/password-reset'));
  }));

  it('preserves a valid link and entered passwords for retry after a temporary service failure', fakeAsync((): void => {
    open('/reset-password#token=' + token);
    input('password', 'NewPass123'); input('passwordConfirmation', 'NewPass123'); submit();
    request('/password-reset').flush({}, {status: 503, statusText: 'Unavailable'});
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent).toContain('try again');
    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[formControlName="password"]')?.value).toBe('NewPass123');
    submit();
    request('/password-reset').flush(null, {status: 204, statusText: 'No Content'});
    flushMicrotasks(); fixture.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(resetAuth).toHaveBeenCalledTimes(1);
  }));
});
