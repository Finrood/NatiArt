import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { CartModalComponent } from './cart-modal.component';

describe('CartModalComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CartModalComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(CartModalComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
