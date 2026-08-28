import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { SignupProfileComponent } from './signup-profile.component';

describe('SignupProfileComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignupProfileComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(SignupProfileComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
