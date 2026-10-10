import {provideRouter} from '@angular/router';
import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {AdminOrderManagementComponent} from './admin-order-management.component';
import {OrderDto} from '../../../models/order.model';

describe('Rendered order fulfillment', (): void => {
  function order(id: string, status: string): OrderDto {
    return {id, status, firstname: 'Buyer', lastname: 'Customer', email: 'buyer@example.test',
      country: 'Brazil', state: 'SP', city: 'City', neighborhood: 'Area', street: 'Street',
      zipCode: '01001000', items: [], totalAmount: 10};
  }
  beforeEach(async (): Promise<void> => {
    await TestBed.configureTestingModule({imports: [AdminOrderManagementComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]}).compileComponents();
  });
  afterEach((): void => {
    const http: HttpTestingController = TestBed.inject(HttpTestingController);
    http.match(request => request.url.endsWith('/admin/order-workspace')).filter(request => !request.cancelled).forEach(request => request.flush({
      awaitingPayment: 1, readyToPrepare: 1, preparing: 1, inTransit: 1, failedNotifications: 0
    }));
    http.verify();
  });
  it('shows only supported actions and keeps command failures on their row', (): void => {
    const fixture = TestBed.createComponent(AdminOrderManagementComponent); fixture.detectChanges();
    const http: HttpTestingController = TestBed.inject(HttpTestingController);
    http.expectOne(request => request.url.endsWith('/admin/orders') && request.params.get('size') === '20')
      .flush([order('pending', 'PENDING'), order('paid', 'PAID'), order('processing', 'PROCESSING'), order('shipped', 'SHIPPED')]);
    fixture.detectChanges();
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('article button'));
    expect(buttons.map(button => button.textContent!.trim())).toEqual(['Mark as processing', 'Record shipment', 'Mark as delivered']);
    buttons[0].click();
    const patch = http.expectOne(request => request.method === 'PATCH' && request.url.endsWith('/admin/orders/paid/status'));
    expect(patch.request.body).toEqual({status: 'PROCESSING'});
    patch.flush('Conflict', {status: 409, statusText: 'Conflict'}); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('article').length).toBe(4);
    const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('article'));
    expect(rows[1].querySelector('[role=alert]')).not.toBeNull();
    expect(rows[0].querySelector('[role=alert]')).toBeNull();
    buttons[1].click(); fixture.detectChanges();
    const form: HTMLFormElement = fixture.nativeElement.querySelector('form');
    const code: HTMLInputElement = form.querySelector('input[formcontrolname=trackingCode]')!;
    code.value = 'BR123456789'; code.dispatchEvent(new Event('input')); form.dispatchEvent(new Event('submit'));
    http.expectOne(request => request.url.endsWith('/admin/orders/processing/shipment'))
      .flush(order('processing', 'SHIPPED')); fixture.detectChanges();
    expect(rows[2].textContent).toContain('Mark as delivered');
  });
  it('loads an older actionable 21st order on page two and fulfills it through the real service', (): void => {
    const fixture = TestBed.createComponent(AdminOrderManagementComponent); fixture.detectChanges();
    const http: HttpTestingController = TestBed.inject(HttpTestingController);
    http.expectOne(request => request.url.endsWith('/admin/orders') && request.params.get('page') === '0')
      .flush(Array.from({length: 20}, (_, index: number) => order('recent-' + index, 'DELIVERED')));
    fixture.detectChanges();
    const more: HTMLButtonElement = fixture.nativeElement.querySelector('[data-load-more]');
    expect(more.disabled).toBeFalse(); more.click();
    http.expectOne(request => request.url.endsWith('/admin/orders') && request.params.get('page') === '1' && request.params.get('size') === '20')
      .flush([order('older-paid', 'PAID')]); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('article').length).toBe(21);
    expect(fixture.nativeElement.querySelector('[data-load-more]')).toBeNull();
    const older: HTMLElement = fixture.nativeElement.querySelectorAll('article')[20];
    older.querySelector<HTMLButtonElement>('button')!.click();
    const patch = http.expectOne(request => request.method === 'PATCH' && request.url.endsWith('/admin/orders/older-paid/status'));
    expect(patch.request.body.status).toBe('PROCESSING'); patch.flush(order('older-paid', 'PROCESSING'));
    fixture.detectChanges(); expect(older.textContent).toContain('Record shipment');
  });
  it('shows purchase snapshots, delivery and protected artwork with failure feedback', (): void => {
    const fixture = TestBed.createComponent(AdminOrderManagementComponent); fixture.detectChanges();
    const http: HttpTestingController = TestBed.inject(HttpTestingController);
    const bought: OrderDto = {...order('paid', 'PAID'), houseNumber: '123', complement: 'Apt 4',
      items: [{id: 'line-1', productId: 'renamed', productLabel: 'Purchased plate', quantity: 2, price: 5,
        personalization: {personalizationOptions: {GOLDEN_BORDER: 'true', CUSTOM_IMAGE: 'opaque-upload'}}}]};
    http.expectOne(request => request.url.endsWith('/admin/orders')).flush([bought]); fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Purchased plate');
    expect(root.textContent).toContain('Quantity: 2');
    expect(root.textContent).toContain('Street, 123');
    expect(root.textContent).toContain('Gold border');
    const button: HTMLButtonElement = Array.from(root.querySelectorAll('button'))
      .find((element: HTMLButtonElement): boolean => element.textContent?.trim() === 'View custom artwork')!;
    button.click(); fixture.detectChanges();
    expect(root.textContent).toContain('Loading artwork');
    http.expectOne(request => request.url.endsWith('/admin/orders/paid/items/line-1/artwork'))
      .flush(new Blob(['Missing'], {type: 'text/plain'}), {status: 404, statusText: 'Not Found'});
    fixture.detectChanges(); expect(root.textContent).toContain('Artwork could not be loaded');
    fixture.destroy();
  });

  it('uses server queues so older paid orders remain discoverable and failed emails can be retried', (): void => {
    const fixture = TestBed.createComponent(AdminOrderManagementComponent); fixture.detectChanges();
    const http: HttpTestingController = TestBed.inject(HttpTestingController);
    http.expectOne(request => request.url.endsWith('/admin/orders')).flush([]);
    http.expectOne(request => request.url.endsWith('/admin/order-workspace')).flush({
      awaitingPayment: 1, readyToPrepare: 3, preparing: 0, inTransit: 0, failedNotifications: 1
    }); fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    const queue: HTMLButtonElement = Array.from(root.querySelectorAll('nav button'))[0] as HTMLButtonElement;
    queue.click();
    http.expectOne(request => request.url.endsWith('/admin/order-workspace/queue') && request.params.get('status') === 'PAID').flush([order('older', 'PAID')]); fixture.detectChanges();
    expect(root.textContent).toContain('#older');
    fixture.componentInstance.openNotifications();
    http.expectOne(request => request.url.endsWith('/admin/order-notifications/attention')).flush([
      {id: 'older:PAID', orderId: 'older', milestone: 'PAID', attempts: 8, exhausted: true, nextAttemptAt: '2026-10-10'}
    ]); fixture.detectChanges(); expect(root.textContent).toContain('Automatic retries paused');
    fixture.componentInstance.retryNotification(fixture.componentInstance.$notifications()[0]);
    const retry = http.expectOne(request => request.url.endsWith('/admin/order-notifications/older%3APAID/retry'));
    expect(retry.request.method).toBe('POST'); retry.flush(null);
    expect(fixture.componentInstance.$notifications()).toEqual([]);
  });
  it('does not ship without a tracking code and keeps carrier validation errors visible', (): void => {
    const fixture = TestBed.createComponent(AdminOrderManagementComponent); fixture.detectChanges();
    const http: HttpTestingController = TestBed.inject(HttpTestingController);
    http.expectOne(request => request.url.endsWith('/admin/orders')).flush([order('packing', 'PROCESSING')]); fixture.detectChanges();
    fixture.componentInstance.startShipment(fixture.componentInstance.orders[0]);
    fixture.componentInstance.ship(fixture.componentInstance.orders[0]);
    http.expectNone(request => request.url.endsWith('/shipment'));
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('Enter a carrier reference');
    fixture.componentInstance.shipmentForm.setValue({trackingCode: 'BR123', trackingUrl: 'https://carrier.example.test/BR123'});
    fixture.componentInstance.ship(fixture.componentInstance.orders[0]);
    const req = http.expectOne(request => request.url.endsWith('/admin/orders/packing/shipment'));
    expect(req.request.body).toEqual({trackingCode: 'BR123', trackingUrl: 'https://carrier.example.test/BR123'});
    req.flush({}, {status: 409, statusText: 'Conflict'}); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Shipment could not be recorded');
    expect(fixture.componentInstance.$shippingOrderId()).toBe('packing');
  });

});
