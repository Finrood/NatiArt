import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';

import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('navigates to the working collection from the store menu', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const router: Router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture.detectChanges();
    const button: HTMLAnchorElement = fixture.nativeElement.querySelector('a[routerLink="/products"]');
    expect(button).toBeTruthy();
    button.click();
    expect(navigate).toHaveBeenCalled();
    expect(navigate.calls.mostRecent().args[0].toString()).toBe('/products');
  });

  it('should create' , () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
