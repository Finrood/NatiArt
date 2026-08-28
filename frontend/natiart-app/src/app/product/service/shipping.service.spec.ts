import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { ShippingService } from './shipping.service';

describe('ShippingService', () => {
  let service: ShippingService;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting()]});
    service = TestBed.inject(ShippingService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
