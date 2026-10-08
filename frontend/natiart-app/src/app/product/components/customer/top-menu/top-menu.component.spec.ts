import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, of } from 'rxjs';

import { TopMenuComponent } from './top-menu.component';
import { CartService } from '../../../service/cart.service';
import { AuthenticationService } from '../../../../directory/service/authentication.service';
import {CartItem} from '../../../models/CartItem.model';

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

  it('closes the preview after pointer exit and cancels exit when the pointer returns', fakeAsync(() => {
    const fixture = TestBed.createComponent(TopMenuComponent);
    const component: TopMenuComponent = fixture.componentInstance;
    component.showCartModal(); component.hideCartModal(); tick(201);
    expect(component.isCartHovered).toBeFalse();
    component.showCartModal(); component.hideCartModal(); tick(100);
    component.showCartModal(); tick(201);
    expect(component.isCartHovered).toBeTrue();
    component.closeCartPreview();
    expect(component.isCartHovered).toBeFalse();
    fixture.destroy();
  }));

  it('restores the cart link after Escape inside the preview without stealing outside focus', async () => {
    const item: CartItem = {cartItemId: 'line-1', quantity: 1, product: {
      id: 'vase', label: 'Vase', originalPrice: 20, markedPrice: 20, stockQuantity: 10,
      categoryId: 'ceramics', availablePersonalizations: [], tags: [], images: [],
    }};
    TestBed.overrideProvider(CartService, {useValue: {
      getCartCount: (): Observable<number> => of(1),
      getCartItems: (): Observable<CartItem[]> => of([item]),
      getCartTotal: (): Observable<number> => of(20),
    }});
    const fixture = TestBed.createComponent(TopMenuComponent);
    const host: HTMLElement = fixture.nativeElement as HTMLElement;
    document.body.appendChild(host);
    try {
      fixture.detectChanges();
      host.querySelector('.cart-container')?.dispatchEvent(new MouseEvent('mouseenter'));
      fixture.detectChanges();
      await fixture.whenStable();
      const input: HTMLInputElement | null = host.querySelector('app-cart-modal input');
      expect(input).not.toBeNull();
      input?.focus();
      document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));
      fixture.detectChanges();
      expect(host.querySelector('app-cart-modal')).toBeNull();
      expect(document.activeElement).toBe(host.querySelector('a[aria-label="Shopping cart"]'));

      const home: HTMLAnchorElement = host.querySelector('a[href="/dashboard"]') as HTMLAnchorElement;
      home.focus();
      host.querySelector('.cart-container')?.dispatchEvent(new MouseEvent('mouseenter'));
      fixture.detectChanges();
      document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));
      fixture.detectChanges();
      expect(document.activeElement).toBe(home);
    } finally {
      fixture.destroy();
      host.remove();
    }
  });
});
