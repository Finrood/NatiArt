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

  it('uploadsCustomerArtworkAsMultipartDataAndReturnsOpaqueId', () => {
    const file = new File(['art'], 'art.png', {type: 'image/png'});
    let uploadId: string | undefined;
    service.uploadCustomerImage(file).subscribe(response => uploadId = response.uploadId);

    const request = httpMock.expectOne('http://localhost:8082/customer/uploads');
    expect(request.request.method).toBe('POST');
    expect(request.request.body instanceof FormData).toBeTrue();
    const uploaded = (request.request.body as FormData).get('file') as File;
    expect(uploaded.name).toBe(file.name);
    expect(uploaded.type).toBe(file.type);
    expect(uploaded.size).toBe(file.size);
    request.flush({uploadId: 'upload-1'});

    expect(uploadId).toBe('upload-1');
  });
});
