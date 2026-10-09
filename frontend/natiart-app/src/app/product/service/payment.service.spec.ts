import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { PaymentService } from './payment.service';
import { PaymentCreationResponse } from '../models/paymentCreationResonse.model';
import { environment } from '../../../environments/environment';

describe('PaymentService', () => {
  let service: PaymentService;
  let http: HttpTestingController;
  const apiUrl: string = `${environment.api.product.url}`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PaymentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('creates a PIX payment against the payment endpoint', () => {
    const response: PaymentCreationResponse = {
      paymentId: 'pay_123',
      creationDate: new Date('2030-01-01T00:00:00Z'),
      customerId: 'cus_1',
      billingType: 'PIX',
      status: 'PENDING',
      dueDate: new Date('2030-01-02T00:00:00Z'),
      invoiceUrl: 'https://example.test/invoice',
      invoiceNumber: '001',
    };

    service
      .createPixPayment({ paymentProcessor: 'ASAAS', customerId: 'cus_1', billingType: 'PIX', value: 10 })
      .subscribe((actual: PaymentCreationResponse) => {
        expect(actual.paymentId).toBe('pay_123');
      });

    const request = http.expectOne(`${apiUrl}/account/payments/create`);
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    request.flush(response);
  });

  it('reuses a caller supplied idempotency key', () => {
    service
      .createPixPayment(
        { paymentProcessor: 'ASAAS', customerId: 'cus_1', billingType: 'PIX', value: 10 },
        'payment-attempt-1',
      )
      .subscribe();

    const request = http.expectOne(`${apiUrl}/account/payments/create`);
    expect(request.request.headers.get('Idempotency-Key')).toBe('payment-attempt-1');
    request.flush({});
  });

  it('maps the QR payload and parses the expiration date', () => {
    service.getPixQrCode('pay_123').subscribe((data: { encodedImage: string; payload: string; expirationDate: Date }) => {
      expect(data.payload).toBe('payload');
      expect(data.expirationDate instanceof Date).toBeTrue();
    });

    http
      .expectOne(`${apiUrl}/account/payments/pay_123/pix-qr-code`)
      .flush({ success: true, encodedImage: 'abc', payload: 'payload', expirationDate: '2030-01-01T00:00:00Z' });
  });

  for (const invalid of [
    {success: false, encodedImage: 'abc', payload: 'x', expirationDate: '2030-01-01'},
    {success: true, payload: 'x', expirationDate: '2030-01-01'},
    {success: true, encodedImage: '', payload: 'x', expirationDate: '2030-01-01'},
    {success: true, encodedImage: 'abc', payload: ' ', expirationDate: '2030-01-01'},
    {success: true, encodedImage: 'abc', payload: 'x', expirationDate: 'invalid'},
    {success: true, encodedImage: 'abc', payload: 'x'},
  ]) {
    it('rejects malformed raw QR data before constructing an image URL: ' + JSON.stringify(invalid), () => {
      let rejected: boolean = false;
      service.getPixQrCode('pay_123').subscribe({next: () => fail('invalid QR emitted'), error: () => rejected = true});
      http.expectOne(`${apiUrl}/account/payments/pay_123/pix-qr-code`).flush(invalid);
      expect(rejected).toBeTrue();
    });
  }

  it('fetches payment status from the status endpoint', () => {
    service
      .getPaymentStatus('pay_123')
      .subscribe((actual: { paymentId: string; status: string }) => {
        expect(actual.status).toBe('PENDING');
      });

    http.expectOne(`${apiUrl}/account/payments/pay_123/status`).flush({ paymentId: 'pay_123', status: 'PENDING' });
  });
});
