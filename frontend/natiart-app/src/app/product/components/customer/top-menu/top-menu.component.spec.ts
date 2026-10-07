import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, of } from 'rxjs';

import { TopMenuComponent } from './top-menu.component';
import { CartService } from '../../../service/cart.service';
import { AuthenticationService } from '../../../../directory/service/authentication.service';

describe('TopMenuComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TopMenuComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: CartService, useValue: { getCartCount: (): Observable<number> => of(0) } },
        {
          provide: AuthenticationService,
          useValue: { isLoggedIn$: new BehaviorSubject<boolean>(false).asObservable() },
        },
      ],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(TopMenuComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('offers matching account/logout destinations on desktop and mobile and closes the mobile navigation', () => {
    const fixture = TestBed.createComponent(TopMenuComponent);
    fixture.detectChanges();
    fixture.componentInstance.isLoggedIn = true;
    fixture.detectChanges();
    const desktop: HTMLElement = fixture.nativeElement.querySelector('.hidden.lg\\:block');
    expect(desktop.querySelector('a[href="/account"]')).not.toBeNull();
    expect(desktop.querySelector('a[href="/logout"]')).not.toBeNull();
    (fixture.nativeElement.querySelector('button[aria-label="Toggle navigation"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const nav: HTMLElement = fixture.nativeElement.querySelector('nav.flex-col');
    expect(nav.querySelector('a[href="/account"]')).not.toBeNull();
    expect(nav.querySelector('a[href="/logout"]')).not.toBeNull();
    nav.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('nav.flex-col')).toBeNull();
    expect(fixture.nativeElement.querySelector('input[type="search"]')).toBeNull();
    fixture.destroy();
  });

  it('cancels the pending hover-close timer on destroy (P2)', () => {
    const fixture = TestBed.createComponent(TopMenuComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    const clearSpy: jasmine.Spy = spyOn(window, 'clearTimeout').and.callThrough();

    component.showCartModal();
    component.hideCartModal();
    const internals = component as unknown as {
      cartHoverCloseTimer: ReturnType<typeof setTimeout> | undefined;
    };
    expect(internals.cartHoverCloseTimer).toBeDefined();

    fixture.destroy();
    expect(clearSpy).toHaveBeenCalled();
    expect(internals.cartHoverCloseTimer).toBeUndefined();
  });
});
