import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { ShippingInfoStepComponent } from './shipping-info-step.component';

describe('ShippingInfoStepComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShippingInfoStepComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ShippingInfoStepComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
