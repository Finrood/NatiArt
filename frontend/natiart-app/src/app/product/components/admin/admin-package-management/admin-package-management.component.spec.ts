import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { PackageManagementComponent } from './admin-package-management.component';

describe('PackageManagementComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PackageManagementComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(PackageManagementComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
