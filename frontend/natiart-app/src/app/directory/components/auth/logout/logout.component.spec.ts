import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { fakeAsync, tick } from '@angular/core/testing';

import { LogoutComponent } from './logout.component';
import { environment } from '../../../../../environments/environment';

describe('LogoutComponent', () => {
  const LOGOUT_URL: string =
    `${environment.api.directory.url}${environment.api.directory.endpoints.logout}`;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LogoutComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(LogoutComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('navigates to login 2s after a successful logout', fakeAsync(() => {
    const navigateSpy: jasmine.Spy = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const httpTesting: HttpTestingController = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(LogoutComponent);
    fixture.detectChanges();
    httpTesting.expectOne(LOGOUT_URL).flush({});
    expect(fixture.componentInstance.loggedOut).toBeTrue();

    navigateSpy.calls.reset();
    tick(1999);
    expect(navigateSpy).not.toHaveBeenCalled();
    tick(1);
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
    httpTesting.verify();
  }));

  it('cancels the pending login redirect when destroyed', fakeAsync(() => {
    const navigateSpy: jasmine.Spy = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const httpTesting: HttpTestingController = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(LogoutComponent);
    fixture.detectChanges();
    httpTesting.expectOne(LOGOUT_URL).flush({});

    navigateSpy.calls.reset();
    fixture.destroy();
    tick(5000);
    expect(navigateSpy).not.toHaveBeenCalled();
    httpTesting.verify();
  }));
});
