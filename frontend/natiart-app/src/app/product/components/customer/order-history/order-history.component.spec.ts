import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting, HttpTestingController} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {OrderHistoryComponent} from './order-history.component';
import {OrderDto} from '../../../models/order.model';

describe('Customer order history pages', (): void => {
  beforeEach(async (): Promise<void> => {
    localStorage.clear();
    await TestBed.configureTestingModule({imports: [OrderHistoryComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]}).compileComponents();
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
});
