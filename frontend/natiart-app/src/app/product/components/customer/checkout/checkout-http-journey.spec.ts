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
import {CartItem} from '../../../models/CartItem.model';

@Component({imports: [RouterOutlet], template: '<router-outlet />'})
class JourneyHostComponent {}

// Native controls, the real router and both real HTTP services cross the screen boundary.
describe('Rendered checkout HTTP journey', (): void => {
  let fixture: ComponentFixture<JourneyHostComponent>;
  let http: HttpTestingController;
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
    const currentUser: BehaviorSubject<User | null> = new BehaviorSubject<User | null>(user);
    await TestBed.configureTestingModule({
      imports: [JourneyHostComponent], providers: [provideHttpClient(), provideHttpClientTesting(),
        provideRouter([{path: 'checkout', component: CheckoutComponent},
          {path: 'pix-payment/:paymentId', component: PixPaymentConfirmationComponent}]),
        {provide: AuthenticationService, useValue: {
          currentUser$: currentUser.asObservable(), isLoggedIn$: new BehaviorSubject<boolean>(true).asObservable(),
          fetchCurrentUser: (): BehaviorSubject<User | null> => currentUser,
        }},

      ],
    }).compileComponents();
    TestBed.inject(CartService).addToCart(items[0].product, 1).subscribe();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(JourneyHostComponent);
  });

  afterEach((): void => { fixture.destroy(); http.verify(); });

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
});
