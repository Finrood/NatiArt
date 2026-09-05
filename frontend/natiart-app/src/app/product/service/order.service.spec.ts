import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';

import { OrderDto } from '../models/order.model';
import { OrderService } from './order.service';

function makeOrder(): OrderDto {
  return {
    firstname: 'Nati',
    lastname: 'Art',
    email: 'nati@example.com',
    country: 'BR',
    state: 'SC',
    city: 'Floripa',
    neighborhood: 'Centro',
    zipCode: '88010000',
    street: 'Rua XV',
    items: []
  };
}

describe('OrderService', () => {
  let service: OrderService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting()]});
    service = TestBed.inject(OrderService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('resets orderProcessing$ when creation succeeds', () => {
    let latest: boolean | undefined;
    service.orderProcessing$.subscribe((processing: boolean) => latest = processing);

    service.createOrder(makeOrder()).subscribe();
    expect(latest).toBeTrue();
    const req: TestRequest = httpMock.expectOne((request) => request.method === 'POST');
    expect(req.request.url.endsWith('/orders/create')).toBeTrue();
    req.flush(makeOrder());

    expect(latest).toBeFalse();
  });

  it('resets orderProcessing$ when creation fails', () => {
    let latest: boolean | undefined;
    service.orderProcessing$.subscribe((processing: boolean) => latest = processing);

    service.createOrder(makeOrder()).subscribe({error: (): void => undefined});
    expect(latest).toBeTrue();
    const req: TestRequest = httpMock.expectOne((request) => request.method === 'POST');
    req.flush('boom', {status: 500, statusText: 'Server Error'});

    expect(latest).toBeFalse();
  });
});
