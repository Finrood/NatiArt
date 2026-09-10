import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { CategoryManagementComponent } from './admin-category-management.component';

describe('CategoryManagementComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CategoryManagementComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(CategoryManagementComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
