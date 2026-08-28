import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { PixPaymentConfirmationComponent } from './pix-payment-confirmation.component';

describe('PixPaymentConfirmationComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PixPaymentConfirmationComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(PixPaymentConfirmationComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
