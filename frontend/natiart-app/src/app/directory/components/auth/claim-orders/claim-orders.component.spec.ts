import {ComponentFixture} from '@angular/core/testing';
import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {ActivatedRoute, provideRouter, Router} from '@angular/router';
import {ClaimOrdersComponent} from './claim-orders.component';
import {TokenService} from '../../../service/token.service';
import {GUEST_REQUEST} from '../../../../product/service/guest-checkout.service';
import {environment} from '../../../../../environments/environment';

describe('ClaimOrdersComponent verified access', () => {
  const proof: string = 'a'.repeat(43);
  const baseUrl: string = environment.api.directory.url + '/checkout-claim';
  let http: HttpTestingController;
  let fixture: ComponentFixture<ClaimOrdersComponent>;
  let navigation: jasmine.Spy;
  let tokens: jasmine.SpyObj<TokenService>;
  let route: {snapshot: {fragment: string}};

  beforeEach(() => {
    route = {snapshot: {fragment: ''}};
    tokens = jasmine.createSpyObj<TokenService>('TokenService', ['clearTokens']);
    TestBed.configureTestingModule({imports: [ClaimOrdersComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      {provide: ActivatedRoute, useValue: route}, {provide: TokenService, useValue: tokens},
    ]});
    http = TestBed.inject(HttpTestingController);
    navigation = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });
  afterEach(() => http.verify());

  function openVerifiedLink(existingVerifiedAccount: boolean): void {
    route.snapshot.fragment = 'token=' + proof;
    fixture = TestBed.createComponent(ClaimOrdersComponent);
    fixture.detectChanges();
    const inspection = http.expectOne(baseUrl + '/inspect');
    expect(inspection.request.body).toEqual({token: proof});
    expect(inspection.request.context.get(GUEST_REQUEST)).toBeTrue();
    inspection.flush({email: 'buyer@example.test', existingVerifiedAccount});
    fixture.detectChanges();
    expect(navigation).toHaveBeenCalledWith([], jasmine.objectContaining({fragment: undefined, replaceUrl: true}));
  }

  it('requests a link with a uniform result and never logs the visitor in', () => {
    fixture = TestBed.createComponent(ClaimOrdersComponent);
    fixture.detectChanges();
    fixture.componentInstance.emailForm.controls.email.setValue('buyer@example.test');
    fixture.componentInstance.request();
    http.expectOne(baseUrl + '/request').flush(null, {status: 202, statusText: 'Accepted'});
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('If guest details match this email');
    expect(tokens.clearTokens).not.toHaveBeenCalled();
    expect(navigation).not.toHaveBeenCalled();
  });

  it('redeems a mailbox proof for read-only tracking without registering an account', () => {
    openVerifiedLink(false);
    fixture.componentInstance.track();
    const tracking = http.expectOne(baseUrl + '/track');
    expect(tracking.request.withCredentials).toBeTrue();
    expect(tracking.request.body).toEqual({token: proof});
    tracking.flush(null);
    expect(navigation).toHaveBeenCalledWith(['/guest-orders']);
    expect(tokens.clearTokens).not.toHaveBeenCalled();
    fixture.componentInstance.confirm();
    http.expectNone(baseUrl + '/confirm');
  });

  it('requires a strong matching password when activating an unverified account', () => {
    openVerifiedLink(false);
    fixture.componentInstance.passwordForm.setValue({password: 'weak', confirmation: 'weak'});
    fixture.componentInstance.confirm();
    http.expectNone(baseUrl + '/confirm');
    fixture.componentInstance.passwordForm.setValue({password: 'SecureGuest42!', confirmation: 'different'});
    fixture.componentInstance.confirm();
    http.expectNone(baseUrl + '/confirm');
    fixture.componentInstance.passwordForm.setValue({password: 'SecureGuest42!', confirmation: 'SecureGuest42!'});
    fixture.componentInstance.confirm();
    const confirmation = http.expectOne(baseUrl + '/confirm');
    expect(confirmation.request.body).toEqual({token: proof, password: 'SecureGuest42!', passwordConfirmation: 'SecureGuest42!'});
    confirmation.flush(null);
    expect(tokens.clearTokens).toHaveBeenCalledTimes(1);
    expect(navigation.calls.count()).toBe(1);
    expect(fixture.componentInstance.passwordForm.controls.password.value).toBe('');
  });

  it('accepts the current password for an established account without asking to replace it', () => {
    openVerifiedLink(true);
    expect(fixture.nativeElement.querySelector('input[autocomplete="current-password"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('input[autocomplete="new-password"]')).toBeNull();
    fixture.componentInstance.passwordForm.controls.password.setValue('password');
    fixture.componentInstance.confirm();
    const confirmation = http.expectOne(baseUrl + '/confirm');
    expect(confirmation.request.body.passwordConfirmation).toBe('password');
    confirmation.flush({message: 'Invalid credentials'}, {status: 400, statusText: 'Bad Request'});
    expect(tokens.clearTokens).not.toHaveBeenCalled();
    expect(fixture.componentInstance.$success()).toBeFalse();
  });

  it('describes activation password errors, focuses the first invalid field and clears corrected errors', async (): Promise<void> => {
    openVerifiedLink(false);
    const form: HTMLFormElement = fixture.nativeElement.querySelector('form');
    const password: HTMLInputElement = fixture.nativeElement.querySelector('#claim-password');
    const confirmation: HTMLInputElement = fixture.nativeElement.querySelector('#claim-confirmation');
    fixture.componentInstance.passwordForm.setValue({password: 'weak', confirmation: 'different'});
    form.dispatchEvent(new Event('submit', {bubbles: true, cancelable: true}));
    fixture.detectChanges(); await fixture.whenStable();
    expect(password.getAttribute('aria-invalid')).toBe('true');
    expect(password.getAttribute('aria-describedby')).toContain('claim-password-error');
    expect(document.activeElement).toBe(password);
    expect(fixture.nativeElement.querySelector('#claim-password-error').textContent).toContain('uppercase');
    expect(confirmation.getAttribute('aria-invalid')).toBe('true');
    expect(confirmation.getAttribute('aria-describedby')).toContain('claim-confirmation-error');
    expect(fixture.nativeElement.querySelector('#claim-confirmation-error').textContent).toContain('Passwords do not match');
    http.expectNone(baseUrl + '/confirm');

    fixture.componentInstance.passwordForm.setValue({password: 'SecureGuest42!', confirmation: 'SecureGuest42!'});
    fixture.detectChanges(); await fixture.whenStable();
    expect(password.getAttribute('aria-invalid')).toBe('false');
    expect(confirmation.getAttribute('aria-invalid')).toBe('false');
    expect(fixture.nativeElement.querySelector('#claim-confirmation-error')).toBeNull();
    expect(fixture.componentInstance.passwordForm.valid).toBeTrue();
  });
});
