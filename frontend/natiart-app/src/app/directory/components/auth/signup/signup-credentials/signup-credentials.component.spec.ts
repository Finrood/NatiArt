import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { SignupCredentialsComponent } from './signup-credentials.component';

describe('SignupCredentialsComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignupCredentialsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(SignupCredentialsComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
