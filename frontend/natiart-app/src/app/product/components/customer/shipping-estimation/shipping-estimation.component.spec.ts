import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { ShippingEstimationComponent } from './shipping-estimation.component';

describe('ShippingEstimationComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShippingEstimationComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ShippingEstimationComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
