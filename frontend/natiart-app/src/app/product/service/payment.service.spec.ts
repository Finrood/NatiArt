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

    http.expectOne(`${apiUrl}/api/payment/create`).flush(response);
  });

  it('maps the QR payload and parses the expiration date', () => {
    service.getPixQrCode('pay_123').subscribe((data: { encodedImage: string; payload: string; expirationDate: Date }) => {
      expect(data.payload).toBe('payload');
      expect(data.expirationDate instanceof Date).toBeTrue();
    });

    http
      .expectOne(`${apiUrl}/api/payment/pay_123/pixQrCode`)
      .flush({ success: true, encodedImage: 'abc', payload: 'payload', expirationDate: '2030-01-01T00:00:00Z' });
  });

  it('fetches payment status from the status endpoint', () => {
    service
      .getPaymentStatus('pay_123')
      .subscribe((actual: { paymentId: string; status: string }) => {
        expect(actual.status).toBe('PENDING');
      });

    http.expectOne(`${apiUrl}/api/payment/pay_123/status`).flush({ paymentId: 'pay_123', status: 'PENDING' });
  });
});
