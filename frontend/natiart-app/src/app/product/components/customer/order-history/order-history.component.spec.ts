import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting, HttpTestingController} from '@angular/common/http/testing';
import {RouterTestingHarness} from '@angular/router/testing';
import {provideRouter} from '@angular/router';
import {OrderHistoryComponent} from './order-history.component';
import {OrderDto} from '../../../models/order.model';

describe('Customer order history pages', (): void => {
  beforeEach(async (): Promise<void> => {
    localStorage.clear();
    await TestBed.configureTestingModule({imports: [OrderHistoryComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{path: 'account', component: OrderHistoryComponent}])]}).compileComponents();
  });
  afterEach((): void => {TestBed.inject(HttpTestingController).verify(); localStorage.clear();});
  it('renders HTTP results and lets a customer load the older 21st order', (): void => {
    const fixture = TestBed.createComponent(OrderHistoryComponent); fixture.detectChanges();
    const http: HttpTestingController = TestBed.inject(HttpTestingController);
    const entry: OrderDto = {firstname: 'Buyer', lastname: 'Customer', email: 'buyer@example.test', country: 'Brazil',
      state: 'SP', city: 'City', neighborhood: 'Area', zipCode: '01001000', street: 'Street', items: [], totalAmount: 10, status: 'PAID'};
    http.expectOne(request => request.url.endsWith('/orders') && request.params.get('page') === '0' && request.params.get('size') === '20')
      .flush(Array.from({length: 20}, (_, index: number) => ({...entry, id: 'recent-' + index})));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('article').length).toBe(20);
    const more: HTMLButtonElement = fixture.nativeElement.querySelector('main button'); more.click();
    http.expectOne(request => request.url.endsWith('/orders') && request.params.get('page') === '1' && request.params.get('size') === '20')
      .flush([{...entry, id: 'older-21'}]); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('article').length).toBe(21);
    expect(fixture.nativeElement.textContent).toContain('older-21');
    expect(fixture.nativeElement.querySelector('main button')).toBeNull();
  });
  it('opens the purchased order directly and recovers from a missing order to paged history', async (): Promise<void> => {
    const http: HttpTestingController = TestBed.inject(HttpTestingController);
    const harness: RouterTestingHarness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/account?orderId=purchased', OrderHistoryComponent);
    http.expectOne(request => request.url.endsWith('/orders/purchased')).flush({
      id: 'purchased', firstname: 'Buyer', lastname: 'Customer', email: 'buyer@example.test',
      country: 'Brazil', state: 'SP', city: 'City', neighborhood: 'Area',
      zipCode: '01001000', street: 'Street', items: [], totalAmount: 20, status: 'PAID'});
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('purchased');
    expect(harness.routeNativeElement!.querySelector('a[routerLink="/account"]')).not.toBeNull();
    await harness.navigateByUrl('/account?orderId=missing', OrderHistoryComponent);
    http.expectOne(request => request.url.endsWith('/orders/missing')).flush('missing', {status: 404, statusText: 'Not found'});
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).not.toBeNull();
    await harness.navigateByUrl('/account', OrderHistoryComponent);
    http.expectOne(request => request.url.endsWith('/orders') && request.params.get('page') === '0').flush([]);
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('No orders yet');
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).toBeNull();
    harness.fixture.destroy();
  });

  it('retains loaded orders after a failed next page and retries that page', (): void => {
    const fixture = TestBed.createComponent(OrderHistoryComponent); fixture.detectChanges();
    const http: HttpTestingController = TestBed.inject(HttpTestingController);
    const entry: OrderDto = {firstname: 'Buyer', lastname: 'Customer', email: 'buyer@example.test', country: 'Brazil',
      state: 'SP', city: 'City', neighborhood: 'Area', zipCode: '01001000', street: 'Street', items: [], totalAmount: 10, status: 'PAID'};
    http.expectOne(request => request.url.endsWith('/orders') && request.params.get('page') === '0')
      .flush(Array.from({length: 20}, (_, index: number) => ({...entry, id: 'recent-' + index})));
    fixture.detectChanges(); fixture.componentInstance.loadMore();
    http.expectOne(request => request.params.get('page') === '1').flush(null, {status: 503, statusText: 'Unavailable'});
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('article').length).toBe(20);
    const retry: HTMLButtonElement | undefined = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button'))
      .find((button: HTMLButtonElement): boolean => button.textContent?.includes('Retry page') ?? false);
    retry!.click();
    http.expectOne(request => request.params.get('page') === '1').flush([{...entry, id: 'older-21'}]); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('article').length).toBe(21);
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    fixture.destroy();
  });

  it('retries a single owned order while preserving its query parameter', async (): Promise<void> => {
    const http: HttpTestingController = TestBed.inject(HttpTestingController);
    const harness: RouterTestingHarness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/account?orderId=owned', OrderHistoryComponent);
    http.expectOne(request => request.url.endsWith('/orders/owned')).flush(null, {status: 503, statusText: 'Unavailable'});
    harness.detectChanges(); harness.routeNativeElement!.querySelector<HTMLButtonElement>('button')!.click();
    http.expectOne(request => request.url.endsWith('/orders/owned')).flush({id: 'owned', items: [], totalAmount: 10, status: 'PAID'});
    harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('owned');
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).toBeNull();
    harness.fixture.destroy();
  });

});
