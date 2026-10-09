import {TestBed, fakeAsync, tick} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting, TestRequest} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {ActivatedRoute, convertToParamMap} from '@angular/router';
import {BehaviorSubject, of} from 'rxjs';

import {PixPaymentConfirmationComponent} from './pix-payment-confirmation.component';
import {AuthenticationService} from '../../../../../directory/service/authentication.service';
import {CartService} from '../../../../service/cart.service';
import {environment} from '../../../../../../environments/environment';

describe('PixPaymentConfirmationComponent', () => {
  let http: HttpTestingController;
  let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  const statusUrl = `${environment.api.product.url}/payments/pay_123/status`;
  const qrUrl = `${environment.api.product.url}/payments/pay_123/pix-qr-code`;

  beforeEach(async () => {
    paramMap$ = new BehaviorSubject(convertToParamMap({paymentId: 'pay_123'}));
    await TestBed.configureTestingModule({
      imports: [PixPaymentConfirmationComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {provide: AuthenticationService, useValue: {currentUser$: of({externalId: 'cus_MINE'})}},
        {
          provide: ActivatedRoute,
          useValue: {paramMap: paramMap$.asObservable()},
        },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  function createAndFlushQr() {
    const fixture = TestBed.createComponent(PixPaymentConfirmationComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    http.expectOne(statusUrl).flush({status: 'PENDING'});
    http.expectOne(qrUrl).flush({success: true, encodedImage: 'abc', payload: 'x', expirationDate: '2030-01-01T00:00:00Z'});
    return {fixture, component};
  }

  it('should create', () => {
    const {component} = createAndFlushQr();
    expect(component).toBeTruthy();
  });

  it('surfaces a manual-copy fallback when the browser rejects copying', fakeAsync(() => {
    const {fixture, component} = createAndFlushQr();
    tick();
    component.qrCodeData = {
      encodedImage: 'abc',
      payload: 'pix-payload',
      expirationDate: new Date('2030-01-01T00:00:00Z'),
    };
    const input: HTMLInputElement = document.createElement('input');
    input.value = 'pix-payload';
    spyOn(document, 'execCommand').and.returnValue(false);

    component.copyToClipboard(input);
    fixture.detectChanges();

    expect(component.copyFailed).toBeTrue();

    component.ngOnDestroy();
    http.verify();
  }));

  it('announces a successful copy and clears it if the next copy fails', fakeAsync(() => {
    const {fixture, component} = createAndFlushQr();
    fixture.detectChanges();
    const copy: jasmine.Spy = spyOn(document, 'execCommand').and.returnValue(true);
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button[aria-label="Copy PIX code"]');
    button.click();
    fixture.detectChanges();
    expect(component.$copied()).toBeTrue();
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('PIX code copied.');
    copy.and.returnValue(false);
    button.click();
    fixture.detectChanges();
    expect(component.copyFailed).toBeTrue();
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
    fixture.destroy();
    http.verify();
  }));

  it('keeps polling through transient status errors', fakeAsync(() => {
    const {component} = createAndFlushQr();

    for (let i = 0; i < 4; i++) {
      tick(5000);
      http.expectOne(statusUrl).flush('boom', {status: 500, statusText: 'Server Error'});
      tick(0);
      expect(component.paymentStatus).toBe('PENDING');
    }

    // Still polling after 4 consecutive errors: a 5th request happens and a success recovers.
    tick(5000);
    http.expectOne(statusUrl).flush({status: 'PENDING'});
    tick(0);
    expect(component.paymentStatus).toBe('PENDING');

    tick(5000);
    http.match(statusUrl); // draining; no strict assertion here
    component.ngOnDestroy();
    http.verify();
  }));

  it('stops polling and surfaces an error state after 5 consecutive errors', fakeAsync(() => {
    const {component} = createAndFlushQr();

    for (let i = 0; i < 5; i++) {
      tick(5000);
      http.expectOne(statusUrl).flush('boom', {status: 500, statusText: 'Server Error'});
      tick(0);
    }

    expect(component.paymentStatus).toBe('ERROR');

    // Polling must be dead: no more requests fire.
    tick(20000);
    expect(http.match(statusUrl).length).toBe(0);
    http.verify();
  }));

  it('marks COMPLETED and stops polling on success', fakeAsync(() => {
    const {component} = createAndFlushQr();

    tick(5000);
    http.expectOne(statusUrl).flush({status: 'COMPLETED'});
    tick(0);

    expect(component.paymentStatus).toBe('COMPLETED');

    tick(20000);
    expect(http.match(statusUrl).length).toBe(0);
    component.ngOnDestroy();
    http.verify();
  }));

  it('follows the routed payment id: param change restarts QR and polling for the new id', fakeAsync(() => {
    const {component} = createAndFlushQr();
    const newQrUrl = `${environment.api.product.url}/payments/pay_456/pix-qr-code`;
    const newStatusUrl = `${environment.api.product.url}/payments/pay_456/status`;

    paramMap$.next(convertToParamMap({paymentId: 'pay_456'}));
    tick(0);

    expect(component.paymentId).toBe('pay_456');
    http.expectOne(newStatusUrl).flush({status: 'PENDING'});
    http.expectOne(newQrUrl).flush({success: true, encodedImage: 'def', payload: 'y', expirationDate: '2030-01-01T00:00:00Z'});

    // Old payment is no longer polled; the new one is.
    tick(5000);
    expect(http.match(statusUrl).length).toBe(0);
    http.expectOne(newStatusUrl).flush({status: 'PENDING'});
    tick(0);
    expect(component.paymentStatus).toBe('PENDING');

    component.ngOnDestroy();
    http.verify();
  }));

  it('stops polling and surfaces an error after the max poll attempts', fakeAsync(() => {
    const {component} = createAndFlushQr();

    for (let i = 0; i < 60; i++) {
      tick(5000);
      http.expectOne(statusUrl).flush({status: 'PENDING'});
      tick(0);
    }

    expect(component.paymentStatus).toBe('ERROR');

    // Polling must be dead: no more requests fire.
    tick(60000);
    expect(http.match(statusUrl).length).toBe(0);
    component.ngOnDestroy();
    http.verify();
  }));

  it('cancels the in-flight QR lookup when the routed payment changes', fakeAsync(() => {
    const fixture = TestBed.createComponent(PixPaymentConfirmationComponent);
    const component = fixture.componentInstance;
    const newQrUrl = `${environment.api.product.url}/payments/pay_456/pix-qr-code`;
    fixture.detectChanges();
    http.expectOne(statusUrl).flush({status: 'PENDING'});

    paramMap$.next(convertToParamMap({paymentId: 'pay_456'}));
    tick(0);

    // The stale pay_123 QR request was cancelled (cancelled requests stay
    // listed until verified, so assert the flag, not absence); only the new
    // lookup remains open.
    const stale: TestRequest[] = http.match(qrUrl);
    expect(stale.length).toBe(1);
    expect(stale[0].cancelled).toBeTrue();
    http.expectOne(`${environment.api.product.url}/payments/pay_456/status`).flush({status: 'PENDING'});
    http.expectOne(newQrUrl).flush({success: true, encodedImage: 'def', payload: 'y', expirationDate: '2030-01-01T00:00:00Z'});

    component.ngOnDestroy();
    http.verify();
  }));

  it('hides the prior QR while a second lookup is delayed and after it fails', fakeAsync(() => {
    const {fixture, component} = createAndFlushQr();
    paramMap$.next(convertToParamMap({paymentId: 'pay_456'}));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    http.expectOne(`${environment.api.product.url}/payments/pay_456/status`).flush({status: 'PENDING'});
    http.expectOne(`${environment.api.product.url}/payments/pay_456/pix-qr-code`)
      .flush('bad', {status: 502, statusText: 'Bad Gateway'});
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(component.paymentStatus).toBe('ERROR');
    component.ngOnDestroy();
    http.verify();
  }));

  it('hides an expired QR on time even while a status request is outstanding', fakeAsync(() => {
    const fixture = TestBed.createComponent(PixPaymentConfirmationComponent);
    fixture.detectChanges();
    http.expectOne(statusUrl).flush({status: 'PENDING'});
    http.expectOne(qrUrl).flush({success: true, encodedImage: 'abc', payload: 'x',
      expirationDate: new Date(Date.now() + 6000).toISOString()});
    tick(5000);
    const pending: TestRequest = http.expectOne(statusUrl);
    tick(1000);
    fixture.detectChanges();
    expect(pending.cancelled).toBeTrue();
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.componentInstance.paymentStatus).toBe('EXPIRED');
    fixture.destroy();
    http.verify();
  }));

  it('does not overlap slow requests and cancels the previous poll before retrying the same payment', fakeAsync(() => {
    const {component} = createAndFlushQr();
    tick(5000);
    const old: TestRequest = http.expectOne(statusUrl);
    tick(6000);
    http.expectNone(statusUrl);
    expect(old.cancelled).toBeFalse();
    component.retryPayment();
    expect(old.cancelled).toBeTrue();
    http.expectOne(statusUrl).flush({status: 'PENDING'});
    http.expectOne(qrUrl).flush({success: true, encodedImage: 'abc', payload: 'x', expirationDate: '2030-01-01'});
    tick(5000);
    http.expectOne(statusUrl).flush({status: 'COMPLETED', orderId: 'ord-1'});
    expect(component.$orderId()).toBe('ord-1');
    http.expectNone(`${environment.api.product.url}/payments/create`);
    component.ngOnDestroy();
    http.verify();
  }));

  it('reconciles completed payment on refresh without requesting a fresh QR or charge', fakeAsync(() => {
    const {component} = createAndFlushQr();
    component.retryPayment();
    http.expectOne(statusUrl).flush({status: 'COMPLETED', orderId: 'ord-1'});
    expect(component.paymentStatus).toBe('COMPLETED');
    http.expectNone(qrUrl);
    http.expectNone(`${environment.api.product.url}/payments/create`);
    component.ngOnDestroy();
    http.verify();
  }));

  it('reconciles a completed route before requesting any QR or starting polling', fakeAsync(() => {
    const fixture = TestBed.createComponent(PixPaymentConfirmationComponent);
    const complete: jasmine.Spy = spyOn(TestBed.inject(CartService), 'completePurchase');
    fixture.detectChanges();
    http.expectNone(qrUrl);
    http.expectOne(statusUrl).flush({status: 'COMPLETED', orderId: 'ord-1'});
    fixture.detectChanges();
    expect(complete).toHaveBeenCalledOnceWith('ord-1', 'cus_MINE');
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Payment Successful!');
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    tick(5000);
    http.expectNone(statusUrl);
    http.expectNone(qrUrl);
    http.expectNone(`${environment.api.product.url}/payments/create`);
    fixture.destroy();
    http.verify();
  }));

  it('cancels the initial status lookup when the routed payment changes', fakeAsync(() => {
    const fixture = TestBed.createComponent(PixPaymentConfirmationComponent);
    fixture.detectChanges();
    const stale: TestRequest = http.expectOne(statusUrl);
    paramMap$.next(convertToParamMap({paymentId: 'pay_456'}));
    expect(stale.cancelled).toBeTrue();
    http.expectOne(`${environment.api.product.url}/payments/pay_456/status`)
      .flush('unavailable', {status: 503, statusText: 'Service Unavailable'});
    fixture.detectChanges();
    expect(fixture.componentInstance.paymentStatus).toBe('ERROR');
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    http.expectNone(qrUrl);
    fixture.destroy();
    http.verify();
  }));

  it('confirms payment without a celebration timer when reduced motion is requested', fakeAsync(() => {
    spyOn(window, 'matchMedia').and.returnValue({matches: true} as MediaQueryList);
    const fixture = TestBed.createComponent(PixPaymentConfirmationComponent);
    fixture.detectChanges();
    http.expectOne(statusUrl).flush({status: 'COMPLETED', orderId: 'ord-1'});
    fixture.detectChanges();
    const internals: {fireworksTimer: ReturnType<typeof setInterval> | null} =
      fixture.componentInstance as unknown as {fireworksTimer: ReturnType<typeof setInterval> | null};
    expect(internals.fireworksTimer).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Your order is confirmed.');
    fixture.destroy();
    http.verify();
  }));

  it('bounds polling by elapsed time even when requests are slow', fakeAsync(() => {
    const {component} = createAndFlushQr();
    for (let i = 0; i < 29; i++) {
      tick(i === 0 ? 5000 : 4000);
      const pending: TestRequest = http.expectOne(statusUrl);
      tick(6000);
      pending.flush({status: 'PENDING'});
    }
    tick(4000);
    const last: TestRequest = http.expectOne(statusUrl);
    tick(5001);
    expect(last.cancelled).toBeTrue();
    expect(component.paymentStatus).toBe('ERROR');
    component.ngOnDestroy();
    http.verify();
  }));

  it('shows the server order reference and settles its saved cart snapshot on completed reload', fakeAsync(() => {
    const cart: CartService = TestBed.inject(CartService);
    const complete = spyOn(cart, 'completePurchase');
    const {fixture, component} = createAndFlushQr();
    tick(5000);
    http.expectOne(statusUrl).flush({status: 'COMPLETED', orderId: 'ord-1'});
    fixture.detectChanges();
    expect(complete).toHaveBeenCalledOnceWith('ord-1', 'cus_MINE');
    expect(fixture.nativeElement.textContent).toContain('Order reference: ord-1');
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    component.ngOnDestroy();
    http.verify();
  }));

  it('clears the fireworks timer on destroy', fakeAsync(() => {
    const {component} = createAndFlushQr();
    const internals: { fireworksTimer: ReturnType<typeof setInterval> | null } =
      component as unknown as { fireworksTimer: ReturnType<typeof setInterval> | null };

    tick(5000);
    http.expectOne(statusUrl).flush({status: 'COMPLETED'});
    tick(0);

    expect(component.paymentStatus).toBe('COMPLETED');
    expect(internals.fireworksTimer).not.toBeNull();

    component.ngOnDestroy();

    expect(internals.fireworksTimer).toBeNull();
    http.verify();
  }));

  it('surfaces an error state when the QR lookup fails', fakeAsync(() => {
    const fixture = TestBed.createComponent(PixPaymentConfirmationComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    http.expectOne(statusUrl).flush({status: 'PENDING'});
    http.expectOne(qrUrl).flush('boom', {status: 500, statusText: 'Server Error'});
    tick(0);

    expect(component.paymentStatus).toBe('ERROR');

    tick(10000);
    expect(http.match(statusUrl).length).toBe(0);

    fixture.detectChanges();
    const text: string = fixture.nativeElement.textContent as string;
    expect(text).toContain('Could not load the payment details');

    component.ngOnDestroy();
    http.verify();
  }));
});

describe('PixPaymentConfirmationComponent without paymentId', () => {
  let http: HttpTestingController;
  let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(async () => {
    paramMap$ = new BehaviorSubject(convertToParamMap({}));
    await TestBed.configureTestingModule({
      imports: [PixPaymentConfirmationComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {provide: AuthenticationService, useValue: {currentUser$: of({externalId: 'cus_MINE'})}},
        {
          provide: ActivatedRoute,
          useValue: {paramMap: paramMap$.asObservable()},
        },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  it('surfaces an error state without issuing HTTP requests', () => {
    const fixture = TestBed.createComponent(PixPaymentConfirmationComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.paymentId).toBeNull();
    expect(component.paymentStatus).toBe('ERROR');
    http.verify();
  });
});
