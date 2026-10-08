import {ComponentFixture, TestBed, fakeAsync, tick} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';

import {ShippingEstimationComponent} from './shipping-estimation.component';
import {CartItem} from '../../../models/CartItem.model';
import {ShippingEstimate} from '../../../service/shipping.service';

describe('ShippingEstimationComponent', () => {
  let fixture: ComponentFixture<ShippingEstimationComponent>;
  let http: HttpTestingController;
  let states: {status: string; cheapestOption: ShippingEstimate | null; error: string | null}[];

  const estimate = (price: number): ShippingEstimate =>
    ({service: `svc-${price}`, price, estimatedDeliveryDays: 3});

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShippingEstimationComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  const basket = (quantity: number): CartItem[] => [{cartItemId: 'line', quantity, product: {
    id: 'product-1', label: 'Vase', originalPrice: 100, markedPrice: 90, stockQuantity: 10,
    categoryId: 'category', images: [], tags: [], availablePersonalizations: []
  }}];

  const createComponent = (): ShippingEstimationComponent => {
    fixture = TestBed.createComponent(ShippingEstimationComponent);
    fixture.componentRef.setInput('cartItems', basket(2));
    states = [];
    fixture.componentInstance.shippingState$.subscribe(state => states.push(state));
    fixture.detectChanges();
    return fixture.componentInstance;
  };

  const flushEstimateRequest = (body: object, status = 200): void => {
    const req = http.expectOne(r => r.url.includes('/shipping/basket-estimate'));
    expect(req.request.method).toBe('POST');
    req.flush(body, status === 200 ? {} : {status, statusText: 'Server Error'});
  };

  it('should create', () => {
    createComponent();
    expect(fixture.componentInstance).toBeTruthy();
  });

  for (const method of ['typing', 'formatted paste']) {
    it('requests shipping after ' + method + ' through the rendered CEP formatter', fakeAsync(() => {
      const component = createComponent();
      const input: HTMLInputElement = fixture.nativeElement.querySelector('input[cepFormat]');
      if (method === 'typing') {
        for (const digit of '12345678') {
          input.value += digit;
          input.dispatchEvent(new InputEvent('input', {bubbles: true, inputType: 'insertText', data: digit}));
          tick(16);
        }
      } else {
        input.value = '12345-678';
        input.dispatchEvent(new InputEvent('input', {bubbles: true, inputType: 'insertFromPaste', data: '12345-678'}));
      }
      expect(input.value).toBe('12345-678');
      expect(component.shippingForm.get('cep')!.value).toBe('12345678');
      expect(component.shippingForm.valid).toBeTrue();
      tick(300);
      const request = http.expectOne((r) => r.url.includes('/shipping/basket-estimate'));
      expect(request.request.body.zipCode).toBe('12345678');
      request.flush([estimate(9)]);
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Cheapest Shipping Option');
      fixture.destroy();
      tick(300);
      http.verify();
    }));
  }

  it('surfacesTheCheapestOptionOnceTheDebouncedCepLookupSucceeds', fakeAsync(() => {
    const component = createComponent();
    component.shippingForm.get('cep')!.setValue('12345678');

    tick(300);
    const req = http.expectOne(r => r.url.includes('/shipping/basket-estimate'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body['zipCode']).toBe('12345678');
    req.flush([estimate(15), estimate(9.9), estimate(12)]);

    const last = states[states.length - 1];
    expect(last.status).toBe('success');
    expect(last.cheapestOption!.price).toBe(9.9);
    expect(last.error).toBeNull();
    http.verify();
  }));

  it('surfacesTheErrorStateWhenTheBackendFails', fakeAsync(() => {
    const component = createComponent();
    component.shippingForm.get('cep')!.setValue('12345678');

    tick(300);
    flushEstimateRequest({message: 'boom'}, 500);

    const last = states[states.length - 1];
    expect(last.status).toBe('error');
    expect(last.cheapestOption).toBeNull();
    expect(last.error).toContain('Error fetching shipping estimates');
    http.verify();
  }));

  it('surfacesTheNoOptionsStateWhenTheBackendReturnsNoEstimates', fakeAsync(() => {
    const component = createComponent();
    component.shippingForm.get('cep')!.setValue('12345678');

    tick(300);
    flushEstimateRequest([]);

    const last = states[states.length - 1];
    expect(last.status).toBe('no-options');
    expect(last.cheapestOption).toBeNull();
    http.verify();
  }));

  it('neverReachesTheBackendWhileTheCepIsInvalid', fakeAsync(() => {
    const component = createComponent();
    component.shippingForm.get('cep')!.setValue('12');

    tick(600);

    expect(states[states.length - 1].status).toBe('idle');
    http.verify();
  }));
  it('uses actual basket quantities and refreshes when the basket changes', fakeAsync(() => {
    const component = createComponent();
    component.shippingForm.get('cep')!.setValue('12345678'); tick(300);
    const first = http.expectOne(r => r.url.includes('/shipping/basket-estimate'));
    expect(first.request.body).toEqual({zipCode: '12345678', items: [
      {productId: 'product-1', quantity: 2, personalized: false}
    ]});
    first.flush([estimate(10)]);
    fixture.componentRef.setInput('cartItems', basket(3)); fixture.detectChanges();
    expect(states[states.length - 1].cheapestOption).toBeNull(); tick(300);
    const second = http.expectOne(r => r.url.includes('/shipping/basket-estimate'));
    expect(second.request.body.items[0].quantity).toBe(3);
    second.flush([estimate(15)]); fixture.destroy(); http.verify();
  }));

  it('cancels and clears an old estimate immediately when CEP becomes incomplete', fakeAsync(() => {
    const component = createComponent();
    component.shippingForm.get('cep')!.setValue('12345678'); tick(300);
    const first = http.expectOne(r => r.url.includes('/shipping/basket-estimate'));
    component.shippingForm.get('cep')!.setValue('1234567');
    expect(first.cancelled).toBeTrue();
    expect(component.shippingForm.get('cep')!.enabled).toBeTrue();
    expect(states[states.length - 1].status).toBe('idle');
    fixture.destroy(); http.verify();
  }));

  it('retries the same CEP after failure without forcing the shopper to edit it', fakeAsync(() => {
    const component = createComponent();
    component.shippingForm.get('cep')!.setValue('12345678'); tick(300);
    flushEstimateRequest({}, 503); fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    button.click(); tick(300);
    flushEstimateRequest([estimate(10)]);
    expect(states[states.length - 1].status).toBe('success');
    fixture.destroy(); http.verify();
  }));

});
