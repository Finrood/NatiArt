import {TestBed, fakeAsync, tick} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting, TestRequest} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {ActivatedRoute, convertToParamMap} from '@angular/router';
import {BehaviorSubject} from 'rxjs';

import {PixPaymentConfirmationComponent} from './pix-payment-confirmation.component';
import {environment} from '../../../../../../environments/environment';

describe('PixPaymentConfirmationComponent', () => {
  let http: HttpTestingController;
  let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  const statusUrl = `${environment.api.product.url}/api/payment/pay_123/status`;
  const qrUrl = `${environment.api.product.url}/api/payment/pay_123/pixQrCode`;

  beforeEach(async () => {
    paramMap$ = new BehaviorSubject(convertToParamMap({paymentId: 'pay_123'}));
    await TestBed.configureTestingModule({
      imports: [PixPaymentConfirmationComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
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
    fixture.detectChanges(); // ngOnInit -> loadQrCode + startPolling
    http.expectOne(qrUrl).flush({encodedImage: 'abc', payload: 'x', expirationDate: '2030-01-01T00:00:00Z'});
    return {fixture, component};
  }

  it('should create', () => {
    const {component} = createAndFlushQr();
    expect(component).toBeTruthy();
  });

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
    const newQrUrl = `${environment.api.product.url}/api/payment/pay_456/pixQrCode`;
    const newStatusUrl = `${environment.api.product.url}/api/payment/pay_456/status`;

    paramMap$.next(convertToParamMap({paymentId: 'pay_456'}));
    tick(0);

    expect(component.paymentId).toBe('pay_456');
    http.expectOne(newQrUrl).flush({encodedImage: 'def', payload: 'y', expirationDate: '2030-01-01T00:00:00Z'});

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
    const newQrUrl = `${environment.api.product.url}/api/payment/pay_456/pixQrCode`;
    fixture.detectChanges(); // QR request for pay_123 is in flight

    paramMap$.next(convertToParamMap({paymentId: 'pay_456'}));
    tick(0);

    // The stale pay_123 QR request was cancelled (cancelled requests stay
    // listed until verified, so assert the flag, not absence); only the new
    // lookup remains open.
    const stale: TestRequest[] = http.match(qrUrl);
    expect(stale.length).toBe(1);
    expect(stale[0].cancelled).toBeTrue();
    http.expectOne(newQrUrl).flush({encodedImage: 'def', payload: 'y', expirationDate: '2030-01-01T00:00:00Z'});

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
