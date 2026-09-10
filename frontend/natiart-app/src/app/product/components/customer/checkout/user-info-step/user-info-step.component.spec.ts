import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { UserInfoStepComponent } from './user-info-step.component';

describe('UserInfoStepComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserInfoStepComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(UserInfoStepComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
