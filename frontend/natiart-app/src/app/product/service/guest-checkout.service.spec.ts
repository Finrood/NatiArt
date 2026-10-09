import {TestBed} from '@angular/core/testing';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {GuestCheckoutService, GuestSession} from './guest-checkout.service';
import {PaymentService} from './payment.service';
import {environment} from '../../../environments/environment';
import {jwtInterceptor} from '../../directory/interceptors/jwt-interceptor.service';
import {TokenService} from '../../directory/service/token.service';

const session: GuestSession = {id: 'session-id', customerId: 'customer-id', email: 'guest@example.test',
  profile: {firstname: 'Guest', lastname: 'Buyer', cpf: '52998224725', country: 'Brazil', state: 'SP',
    city: 'Sao Paulo', neighborhood: 'Centro', zipCode: '01001000', street: 'Praca da Se', houseNumber: '10'},
  csrfToken: 'csrf-proof', externalId: 'cus_guest', provisioningStatus: 'SUCCEEDED',
  expiresAt: '2030-01-01T00:00:00Z', remembered: true, attemptJson: null};

describe('GuestCheckoutService scoped credentials', () => {
  let guest: GuestCheckoutService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(withInterceptors([jwtInterceptor])), provideHttpClientTesting(),
      {provide: TokenService, useValue: {accessToken: 'stale-account-token'}}]});
    guest = TestBed.inject(GuestCheckoutService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('shares concurrent session starts and never sends an account bearer token', () => {
    let received: number = 0;
    guest.start().subscribe(() => received++); guest.start().subscribe(() => received++);
    const request = http.expectOne(environment.api.directory.url + '/guest/session');
    expect(request.request.withCredentials).toBeTrue(); expect(request.request.headers.has('Authorization')).toBeFalse();
    expect(request.request.headers.get('X-Guest-Request')).toBe('1'); request.flush(session);
    expect(received).toBe(2); expect(guest.identity().username).toBe('guest:session-id');
    expect('role' in guest.identity()).toBeFalse();
  });
  it('keeps checkout details and the retry draft in the server session with CSRF proof', () => {
    guest.$session.set(session);
    guest.details(session.email!, session.profile!, true).subscribe();
    const details = http.expectOne(environment.api.directory.url + '/guest/session/details');
    expect(details.request.headers.get('X-Guest-CSRF')).toBe('csrf-proof'); expect(details.request.body.remember).toBeTrue(); details.flush(session);
    const attempt: string = JSON.stringify({username: 'guest:session-id', currentOrder: {id: 'new-order'}});
    guest.saveAttempt(attempt).subscribe(); const draft = http.expectOne(environment.api.directory.url + '/guest/session/attempt');
    expect(draft.request.body.attemptJson).toBe(attempt); draft.flush(null);
    guest.saveAttempt(null, 'old-order').subscribe();
    const completion = http.expectOne(environment.api.directory.url + '/guest/session/attempt');
    expect(completion.request.body.expectedOrderId).toBe('old-order'); completion.flush(null);
    expect(guest.$session()!.attemptJson).toBe(attempt);
  });
  it('guest payment reads use the cookie capability without account refresh', () => {
    guest.$session.set(session); guest.$active.set(true);
    TestBed.inject(PaymentService).getPaymentStatus('pay_guest').subscribe();
    const status = http.expectOne(environment.api.product.url + '/guest/payments/pay_guest/status');
    expect(status.request.withCredentials).toBeTrue(); expect(status.request.headers.has('Authorization')).toBeFalse();
    status.flush({paymentId: 'pay_guest', status: 'PENDING'});
    guest.$tracking.set(true);
    TestBed.inject(PaymentService).getPaymentStatus('pay_guest').subscribe();
    http.expectOne(environment.api.product.url + '/guest/tracking/payments/pay_guest/status').flush({paymentId: 'pay_guest', status: 'COMPLETED'});
  });
});
