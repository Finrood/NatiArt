import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';

import {
  ShippingEstimate,
  ShippingEstimateRequest,
  ShippingQuote,
  ShippingQuoteRequest,
  ShippingService
} from './shipping.service';

describe('ShippingService', () => {
  let service: ShippingService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting()]});
    service = TestBed.inject(ShippingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('calculateShipping posts a numeric payload to the estimate sub-path', () => {
    const request: ShippingEstimateRequest = {
      to: '88010000',
      height: 2,
      width: 12.7,
      length: 17,
      weight: 2,
      quantity: 1
    };
    const estimates: ShippingEstimate[] = [{service: 'SEDEX', price: 18.5, estimatedDeliveryDays: 3}];
    let result: ShippingEstimate[] | undefined;
    service.calculateShipping(request).subscribe((response: ShippingEstimate[]) => result = response);

    const req: TestRequest = httpMock.expectOne((request) => request.method === 'POST');
    expect(req.request.url.endsWith('/shipping/estimate')).toBeTrue();
    expect(req.request.body).toEqual(request);
    expect(typeof req.request.body.weight).toBe('number');
    expect(typeof req.request.body.height).toBe('number');
    req.flush(estimates);

    expect(result).toEqual(estimates);
  });

  it('createQuote posts only destination and product quantities to the quote endpoint', () => {
    const request: ShippingQuoteRequest = {
      zipCode: '01001000',
      items: [{productId: 'p1', quantity: 2}],
    };
    const quote: ShippingQuote = {
      quoteId: 'q1', destinationPostalCode: '01001000', serviceId: 'pac', serviceName: 'PAC',
      expiresAt: '2099-01-01T00:00:00Z', itemAmount: 20, shippingAmount: 8, totalAmount: 28,
      items: [{productId: 'p1', quantity: 2, unitPrice: 10, lineAmount: 20}],
    };
    let result: ShippingQuote | undefined;
    service.createQuote(request).subscribe(response => result = response);

    const req: TestRequest = httpMock.expectOne((testRequest) => testRequest.method === 'POST');
    expect(req.request.url.endsWith('/shipping/quote')).toBeTrue();
    expect(req.request.body).toEqual(request);
    expect(req.request.body).not.toEqual(jasmine.objectContaining({weight: jasmine.anything()}));
    req.flush(quote);

    expect(result).toEqual(quote);
  });
});
