import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { PackageManagementComponent } from './admin-package-management.component';

describe('PackageManagementComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PackageManagementComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(PackageManagementComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('rejects package dimensions outside the shipping boundary', () => {
    const fixture = TestBed.createComponent(PackageManagementComponent);
    const form = fixture.componentInstance.packageForm;
    const height = form.get('height')!;

    height.setValue(0);
    expect(height.invalid).toBeTrue();
    height.setValue(0.01);
    expect(height.valid).toBeTrue();
    height.setValue(200);
    expect(height.valid).toBeTrue();
    height.setValue(201);
    expect(height.invalid).toBeTrue();
    form.get('label')!.setValue('x'.repeat(256));
    expect(form.get('label')!.invalid).toBeTrue();
  });
});
