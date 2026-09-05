import {TestBed, fakeAsync, tick} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {ActivatedRoute} from '@angular/router';

import {PixPaymentConfirmationComponent} from './pix-payment-confirmation.component';
import {environment} from '../../../../../../environments/environment';

describe('PixPaymentConfirmationComponent', () => {
  let http: HttpTestingController;
  const statusUrl = `${environment.api.product.url}/api/payment/pay_123/status`;
  const qrUrl = `${environment.api.product.url}/api/payment/pay_123/pixQrCode`;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PixPaymentConfirmationComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {snapshot: {paramMap: {get: (key: string) => (key === 'paymentId' ? 'pay_123' : null)}}},
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
});

describe('PixPaymentConfirmationComponent without paymentId', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PixPaymentConfirmationComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {snapshot: {paramMap: {get: (_key: string) => null}}},
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
