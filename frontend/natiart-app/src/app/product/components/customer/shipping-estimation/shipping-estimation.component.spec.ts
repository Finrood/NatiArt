import {ComponentFixture, TestBed, fakeAsync, tick} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {provideAnimations} from '@angular/platform-browser/animations';

import {ShippingEstimationComponent} from './shipping-estimation.component';
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
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  const createComponent = (): ShippingEstimationComponent => {
    fixture = TestBed.createComponent(ShippingEstimationComponent);
    states = [];
    fixture.componentInstance.shippingState$.subscribe(state => states.push(state));
    fixture.detectChanges();
    return fixture.componentInstance;
  };

  const flushEstimateRequest = (body: object, status = 200): void => {
    const req = http.expectOne(r => r.url.includes('/shipping/estimate'));
    expect(req.request.method).toBe('POST');
    req.flush(body, status === 200 ? {} : {status, statusText: 'Server Error'});
  };

  it('should create', () => {
    createComponent();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('surfacesTheCheapestOptionOnceTheDebouncedCepLookupSucceeds', fakeAsync(() => {
    const component = createComponent();
    component.shippingForm.get('cep')!.setValue('12345-678');

    tick(300);
    const req = http.expectOne(r => r.url.includes('/shipping/estimate'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body['to']).toBe('12345678');
    req.flush([estimate(15), estimate(9.9), estimate(12)]);

    const last = states[states.length - 1];
    expect(last.status).toBe('success');
    expect(last.cheapestOption!.price).toBe(9.9);
    expect(last.error).toBeNull();
    http.verify();
  }));

  it('surfacesTheErrorStateWhenTheBackendFails', fakeAsync(() => {
    const component = createComponent();
    component.shippingForm.get('cep')!.setValue('12345-678');

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
    component.shippingForm.get('cep')!.setValue('12345-678');

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
});

