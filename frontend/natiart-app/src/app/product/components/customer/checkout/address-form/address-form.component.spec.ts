import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { AddressFormComponent } from './address-form.component';

describe('AddressFormComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddressFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(AddressFormComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
