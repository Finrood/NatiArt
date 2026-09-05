import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ProductService } from './product.service';

describe('ProductService', () => {
  let service: ProductService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting()]});
    service = TestBed.inject(ProductService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('emits an error without HTTP when the product id is null', () => {
    const captured: { message: string | null } = { message: null };
    service.getProduct(null).subscribe({
      next: () => fail('expected an error, got a product'),
      error: (error: Error) => { captured.message = error.message; },
    });
    expect(captured.message).toBe('Missing product id');
    httpMock.expectNone(() => true);
  });

  it('emits an error without HTTP when the product id is blank', () => {
    const captured: { message: string | null } = { message: null };
    service.getProduct('   ').subscribe({
      next: () => fail('expected an error, got a product'),
      error: (error: Error) => { captured.message = error.message; },
    });
    expect(captured.message).toBe('Missing product id');
    httpMock.expectNone(() => true);
  });
});
