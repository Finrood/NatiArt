import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';

import { ShippingEstimate, ShippingEstimateRequest, ShippingService } from './shipping.service';

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
});
