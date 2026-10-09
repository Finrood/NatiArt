import {Component} from '@angular/core';
import {ComponentFixture, fakeAsync, flushMicrotasks, TestBed, tick} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting, TestRequest} from '@angular/common/http/testing';
import {provideRouter, Router, RouterOutlet} from '@angular/router';
import {BehaviorSubject} from 'rxjs';
import {CheckoutComponent} from './checkout.component';
import {PixPaymentConfirmationComponent} from './pix-payment-confirmation/pix-payment-confirmation.component';
import {CartService} from '../../../service/cart.service';
import {AuthenticationService} from '../../../../directory/service/authentication.service';
import {RoleName, User} from '../../../../directory/models/user.model';
import {GuestCheckoutService, GuestSession} from '../../../service/guest-checkout.service';
import {CartItem} from '../../../models/CartItem.model';

@Component({imports: [RouterOutlet], template: '<router-outlet />'})
class JourneyHostComponent {}

// Native controls, the real router and both real HTTP services cross the screen boundary.
describe('Rendered checkout HTTP journey', (): void => {
  let fixture: ComponentFixture<JourneyHostComponent>;
  let http: HttpTestingController;
  let currentUser: BehaviorSubject<User | null>;
  let loggedIn: BehaviorSubject<boolean>;
  const user: User = {
    id: 'u1', username: 'user@example.test', externalId: 'cus_1', role: RoleName.USER,
    profile: {firstname: 'Ada', lastname: 'Lovelace', cpf: '52998224725', phone: '11999999999',
      country: 'Brazil', state: 'SP', city: 'Sao Paulo', neighborhood: 'Centro',
      zipCode: '01001000', street: 'Praca da Se'},
  };
  const items: CartItem[] = [{cartItemId: 'line-1', quantity: 1, product: {
    id: 'product-1', label: 'Bowl', originalPrice: 99.9, markedPrice: 99.9, stockQuantity: 2,
    categoryId: 'category-1', availablePersonalizations: [], tags: [], images: [],
  }}];

  beforeEach(async (): Promise<void> => {
    localStorage.removeItem('natiart-cart');
    localStorage.removeItem('natiart-purchases');
    localStorage.removeItem('natiart-checkout-attempt:user%40example.test');
    currentUser = new BehaviorSubject<User | null>(user);
    loggedIn = new BehaviorSubject<boolean>(true);
    await TestBed.configureTestingModule({
      imports: [JourneyHostComponent], providers: [provideHttpClient(), provideHttpClientTesting(),
        provideRouter([{path: 'checkout', component: CheckoutComponent},
          {path: 'pix-payment/:paymentId', component: PixPaymentConfirmationComponent}]),
        {provide: AuthenticationService, useValue: {
          authResolved$: new BehaviorSubject<boolean>(true).asObservable(), currentUser$: currentUser.asObservable(), isLoggedIn$: loggedIn.asObservable(),
          fetchCurrentUser: (): BehaviorSubject<User | null> => currentUser,
        }},

      ],
    }).compileComponents();
    TestBed.inject(CartService).addToCart(items[0].product, 1).subscribe();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(JourneyHostComponent);
  });

  afterEach((): void => {
    for (const restore of http.match(request => request.url.endsWith('/guest/session') && request.method === 'GET')) {
      if (!restore.cancelled) restore.flush({}, {status: 403, statusText: 'No saved guest session'});
    }
    fixture.destroy(); http.verify();
  });

  function click(label: string): void {
    const button: HTMLButtonElement | undefined = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button'))
      .find((element: HTMLButtonElement): boolean => element.textContent?.trim() === label);
    expect(button).withContext(label).toBeDefined();
    expect(button?.disabled).withContext(label).toBeFalse();
    button!.click(); fixture.detectChanges();
  }

  function submit(): TestRequest {
    void TestBed.inject(Router).navigateByUrl('/checkout');
    flushMicrotasks(); fixture.detectChanges();
    click('Next: Shipping');
    const houseNumber: HTMLInputElement = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[formControlName="houseNumber"]')!;
    houseNumber.value = 'N/A'; houseNumber.dispatchEvent(new Event('input', {bubbles: true})); fixture.detectChanges();
    click('Next: Payment');
    flushMicrotasks();
    const quote: TestRequest = http.expectOne((request): boolean => request.url.endsWith('/shipping/quote'));
    expect(quote.request.body.items).toEqual([{productId: 'product-1', quantity: 1}]);
    quote.flush({quoteId: 'quote-1', destinationPostalCode: '01001000', serviceId: 'fixture',
      serviceName: 'Fixture', expiresAt: '2099-01-01T00:00:00Z', itemAmount: 99.9,
      shippingAmount: 7.5, totalAmount: 107.4,
      items: [{productId: 'product-1', quantity: 1, unitPrice: 99.9, lineAmount: 99.9}]});
    flushMicrotasks(); fixture.detectChanges();
    const method: HTMLSelectElement = (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLSelectElement>('select[formControlName="paymentMethod"]')!;
    method.value = 'PIX'; method.dispatchEvent(new Event('change')); fixture.detectChanges();
    click('Place Order'); flushMicrotasks(); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Processing');
    return http.expectOne((request): boolean => request.url.endsWith('/orders/create'));
  }

  it('renders and focuses the next step after a real button click without forcing change detection', async (): Promise<void> => {
    fixture.autoDetectChanges();
    await TestBed.inject(Router).navigateByUrl('/checkout');
    await fixture.whenStable();
    const screen: HTMLElement = fixture.nativeElement as HTMLElement;
    const next: HTMLButtonElement | undefined = Array.from(screen.querySelectorAll<HTMLButtonElement>('button'))
      .find((button: HTMLButtonElement): boolean => button.textContent?.trim() === 'Next: Shipping');
    expect(next).toBeDefined();
    expect(next!.disabled).toBeFalse();
    next!.click();
    await fixture.whenStable();
    const heading: HTMLHeadingElement | null = screen.querySelector<HTMLHeadingElement>('h2[tabindex="-1"]');
    expect(heading?.textContent).toContain('Shipping Address');
    expect(document.activeElement).toBe(heading);
    expect(screen.querySelector<HTMLInputElement>('input[formControlName="houseNumber"]')).not.toBeNull();
    http.expectNone((request): boolean => request.url.endsWith('/shipping/quote'));
  });

  it('uses the committed server total, enters PIX through the router and renders confirmed status', fakeAsync((): void => {
    const order: TestRequest = submit();
    expect(order.request.method).toBe('POST');
    expect(order.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(order.request.body.items[0]).toEqual(jasmine.objectContaining({productId: 'product-1', quantity: 1}));
    order.flush({...order.request.body, id: 'order-1', totalAmount: 107.4, deliveryAmount: 7.5});
    flushMicrotasks(); fixture.detectChanges();
    const payment: TestRequest = http.expectOne((request): boolean => request.url.endsWith('/payments/create'));
    expect(payment.request.method).toBe('POST');
    expect(payment.request.body).toEqual(jasmine.objectContaining({orderId: 'order-1', value: 107.4, customerId: 'cus_1'}));
    expect(payment.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    payment.flush({paymentId: 'pay_1', status: 'PENDING', billingType: 'PIX'});
    flushMicrotasks(); fixture.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/pix-payment/pay_1');
    http.expectOne((request): boolean => request.url.endsWith('/payments/pay_1/status'))
      .flush({paymentId: 'pay_1', status: 'PENDING'});
    http.expectOne((request): boolean => request.url.endsWith('/payments/pay_1/pix-qr-code'))
      .flush({success: true, encodedImage: 'iVBORw0KGgo=', payload: 'fixture-pix', expirationDate: '2030-01-01T00:00:00Z'});
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('img')?.getAttribute('src')).toContain('data:image/png;base64,');
    tick(5000);
    http.expectOne((request): boolean => request.url.endsWith('/payments/pay_1/status'))
      .flush({paymentId: 'pay_1', status: 'COMPLETED', orderId: 'order-1'});
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Payment Successful');
    expect(TestBed.inject(CartService).getCartItemsSnapshot()).toEqual([]);
    expect(localStorage.getItem('natiart-checkout-attempt:user%40example.test')).toBeNull();
    TestBed.inject(CartService).addToCart(items[0].product, 1).subscribe();
    void TestBed.inject(Router).navigateByUrl('/checkout');
    flushMicrotasks(); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Your Information');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Resume saved checkout');
    http.expectNone((request): boolean => request.url.endsWith('/payments/create'));
    fixture.destroy();
  }));

  it('retains an ambiguous order conflict and never sends a payment request', fakeAsync((): void => {
    submit().flush({message: 'Stock changed'}, {status: 409, statusText: 'Conflict'});
    flushMicrotasks(); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent).toContain('Could not confirm your saved checkout');
    expect(localStorage.getItem('natiart-checkout-attempt:user%40example.test')).not.toBeNull();
    expect(TestBed.inject(Router).url).toBe('/checkout');
    http.expectNone((request): boolean => request.url.endsWith('/payments/create'));
  }));
  it('releases a typed pre-acceptance rejection for shipping review without charging', fakeAsync((): void => {
    submit().flush({code: 'ORDER_CREATION_REJECTED', orderCreated: false}, {status: 400, statusText: 'Bad Request'});
    flushMicrotasks(); fixture.detectChanges();
    expect(localStorage.getItem('natiart-checkout-attempt:user%40example.test')).toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Shipping Address');
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).not.toBeNull();
    http.expectNone((request): boolean => request.url.endsWith('/payments/create'));
  }));
  function guestReady(pendingLookup: boolean = false): GuestSession {
    currentUser.next(null); loggedIn.next(false);
    void TestBed.inject(Router).navigateByUrl('/checkout'); flushMicrotasks(); fixture.detectChanges();
    const session: GuestSession = {id: 'guest-session', customerId: 'guest-customer', email: 'guest@example.test',
      profile: {...user.profile, houseNumber: '10'}, csrfToken: 'guest-proof', externalId: 'cus_guest',
      provisioningStatus: 'SUCCEEDED', remembered: false, expiresAt: '2030-01-01T00:00:00Z', attemptJson: null};
    http.expectOne(request => request.method === 'POST' && request.url.endsWith('/guest/session')).flush(session);
    flushMicrotasks(); fixture.detectChanges(); click('Next: Shipping');
    if (pendingLookup) {
      const zipCode: HTMLInputElement = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[formControlName="zipCode"]')!;
      zipCode.value = '01001000'; zipCode.dispatchEvent(new Event('input', {bubbles: true})); tick(400);
    }
    click('Next: Payment'); flushMicrotasks();
    const details: TestRequest = http.expectOne(request => request.url.endsWith('/guest/session/details'));
    expect(details.request.body.email).toBe('guest@example.test'); expect(details.request.body.remember).toBeFalse();
    expect(details.request.headers.get('X-Guest-CSRF')).toBe('guest-proof'); details.flush(session); flushMicrotasks();
    http.expectOne(request => request.url.endsWith('/guest/shipping/quote')).flush({quoteId: 'guest-quote',
      destinationPostalCode: '01001000', serviceId: 'fixture', serviceName: 'Fixture', expiresAt: '2099-01-01T00:00:00Z',
      itemAmount: 80, shippingAmount: 7.5, totalAmount: 87.5,
      items: [{productId: 'product-1', quantity: 1, unitPrice: 80, lineAmount: 80}]});
    flushMicrotasks(); fixture.detectChanges(); return session;
  }
  it('keeps the reviewed guest shipping total when advancing cancels a pending address lookup', fakeAsync((): void => {
    guestReady(true);
    const lookup: TestRequest = http.expectOne(request => request.url.includes('viacep'));
    expect(lookup.cancelled).toBeTrue();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Payment Details');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('R$87.50');
    click('Place Order'); flushMicrotasks();
    http.expectOne(request => request.url.endsWith('/guest/session/attempt')).flush(null); flushMicrotasks();
    http.expectOne(request => request.url.endsWith('/guest/orders/create')).flush({message: 'Try again'}, {status: 503, statusText: 'Unavailable'});
    flushMicrotasks();
  }));
  it('saves guest retry keys before order creation and completes PIX without login or registration', fakeAsync((): void => {
    const session: GuestSession = guestReady(); click('Place Order'); flushMicrotasks();
    const saved: TestRequest = http.expectOne(request => request.url.endsWith('/guest/session/attempt'));
    const attempt: {orderIdempotencyKey: string; currentOrder: unknown; paymentId: string | null} = JSON.parse(saved.request.body.attemptJson);
    http.expectNone(request => request.url.endsWith('/guest/orders/create'));
    expect(attempt.orderIdempotencyKey).toMatch(/^[a-f0-9-]{36}$/); saved.flush(null); flushMicrotasks();
    const order: TestRequest = http.expectOne(request => request.url.endsWith('/guest/orders/create'));
    expect(order.request.headers.get('Idempotency-Key')).toBe(attempt.orderIdempotencyKey);
    expect(order.request.withCredentials).toBeTrue(); expect(order.request.body.shippingQuoteId).toBe('guest-quote');
    const accepted = {...order.request.body, id: 'guest-order', status: 'PENDING', totalAmount: 87.5};
    order.flush(accepted); flushMicrotasks();
    http.expectOne(request => request.url.endsWith('/guest/session/attempt')).flush(null); flushMicrotasks();
    const payment: TestRequest = http.expectOne(request => request.url.endsWith('/guest/payments/create'));
    expect(payment.request.body.customerId).toBe('cus_guest'); expect(payment.request.body.value).toBe(87.5);
    payment.flush({paymentId: 'guest-payment', status: 'PENDING'}); flushMicrotasks();
    const progress: TestRequest = http.expectOne(request => request.url.endsWith('/guest/session/attempt'));
    const finalDraft: string = progress.request.body.attemptJson; progress.flush(null); flushMicrotasks(); fixture.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/pix-payment/guest-payment?guest=1');
    http.expectOne(request => request.method === 'POST' && request.url.endsWith('/guest/session')).flush({...session, attemptJson: finalDraft});
    flushMicrotasks();
    http.expectOne(request => request.url.endsWith('/guest/payments/guest-payment/status')).flush({paymentId: 'guest-payment', status: 'COMPLETED', orderId: 'guest-order'});
    flushMicrotasks(); fixture.detectChanges();
    const completion: TestRequest = http.expectOne(request => request.url.endsWith('/guest/session/attempt'));
    expect(completion.request.body.expectedOrderId).toBe('guest-order'); completion.flush(null);
    expect(TestBed.inject(CartService).getCartItemsSnapshot()).toEqual([]);
    http.expectNone(request => request.url.endsWith('/register-user') || request.url.endsWith('/login'));
  }));
  it('stops an in-flight guest checkout on sign-in and preserves its guest recovery draft', fakeAsync((): void => {
    guestReady(); click('Place Order'); flushMicrotasks();
    const draft: TestRequest = http.expectOne(request => request.url.endsWith('/guest/session/attempt'));
    const preserved: string = draft.request.body.attemptJson;
    currentUser.next(user); loggedIn.next(true); draft.flush(null); flushMicrotasks(); fixture.detectChanges();
    http.expectNone(request => request.url.endsWith('/orders/create') || request.url.endsWith('/payments/create'));
    expect(TestBed.inject(GuestCheckoutService).$session()!.attemptJson).toBe(preserved);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Resume guest checkout');
  }));

});
