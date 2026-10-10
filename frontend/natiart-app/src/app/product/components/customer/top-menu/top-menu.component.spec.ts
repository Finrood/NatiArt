import { ComponentFixture, TestBed } from '@angular/core/testing';
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
        { provide: CartService, useValue: { getCartCount: (): Observable<number> => of(0), getCartItems: (): Observable<CartItem[]> => of([]), getCartTotal: (): Observable<number> => of(0) } },
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

  it('measures wrapped header height for focus clearance and restores the prior value on destruction', async (): Promise<void> => {
    const root: HTMLElement = document.documentElement;
    const previous: string = root.style.getPropertyValue('--header-clearance');
    root.style.setProperty('--header-clearance', '91px');
    let notify: ResizeObserverCallback;
    const observer: jasmine.SpyObj<ResizeObserver> = jasmine.createSpyObj<ResizeObserver>('ResizeObserver', ['observe', 'disconnect', 'unobserve']);
    spyOn(window, 'ResizeObserver').and.callFake(function(callback: ResizeObserverCallback): ResizeObserver {
      notify = callback; return observer;
    });
    const fixture: ComponentFixture<TopMenuComponent> = TestBed.createComponent(TopMenuComponent);
    try {
      fixture.detectChanges(); await fixture.whenStable();
      const row: HTMLElement = fixture.nativeElement.querySelector('.store-header-row');
      spyOn(row, 'getBoundingClientRect').and.returnValue({height: 143.25} as DOMRect);
      notify!([], observer);
      expect(observer.observe).toHaveBeenCalledWith(row);
      expect(root.style.getPropertyValue('--header-clearance')).toBe('161px');
      fixture.destroy();
      expect(observer.disconnect).toHaveBeenCalledTimes(1);
      expect(root.style.getPropertyValue('--header-clearance')).toBe('91px');
    } finally {
      fixture.destroy();
      if (previous) root.style.setProperty('--header-clearance', previous);
      else root.style.removeProperty('--header-clearance');
    }
  });

  it('exposes administration on desktop and mobile only for an authenticated administrator', () => {
    const loggedIn: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(true);
    let administrator: boolean = true;
    TestBed.overrideProvider(AuthenticationService, {useValue: {
      isLoggedIn$: loggedIn.asObservable(), get isAdmin(): boolean { return administrator; }
    }});
    const fixture = TestBed.createComponent(TopMenuComponent);
    fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;
    expect(host.querySelector('a[href="/admin"]')).not.toBeNull();
    host.querySelector<HTMLButtonElement>('button[aria-label="Toggle navigation"]')!.click();
    fixture.detectChanges();
    expect(host.querySelector('nav.flex-col a[href="/admin"]')).not.toBeNull();
    administrator = false; loggedIn.next(true); fixture.detectChanges();
    expect(host.querySelector('a[href="/admin"]')).toBeNull();
    administrator = true; loggedIn.next(false); fixture.detectChanges();
    expect(host.querySelector('a[href="/admin"]')).toBeNull();
    fixture.destroy();
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

  it('opens a contained drawer only after cart activation and leaves the current page in place', async () => {
    const fixture: ComponentFixture<TopMenuComponent> = TestBed.createComponent(TopMenuComponent);
    fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;
    host.querySelector('.cart-container')!.dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    expect(host.querySelector('dialog')).toBeNull();

    const opener: HTMLButtonElement = host.querySelector('button[aria-label="Shopping cart"]')!;
    opener.focus();
    opener.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const dialog: HTMLDialogElement = host.querySelector('dialog')!;
    expect(dialog.matches(':modal')).toBeTrue();
    expect(dialog.classList.contains('drawer')).toBeTrue();
    expect(opener.getAttribute('aria-expanded')).toBe('true');
    expect(opener.getAttribute('aria-controls')).toBe('cart-preview');
    expect(document.body.style.overflow).toBe('hidden');
    host.querySelector<HTMLAnchorElement>('a[href="/dashboard"]')!.focus();
    expect(dialog.contains(document.activeElement)).toBeTrue();
    fixture.destroy();
  });

  it('dismisses the drawer with its backdrop or Close button and releases scrolling', async () => {
    const fixture: ComponentFixture<TopMenuComponent> = TestBed.createComponent(TopMenuComponent);
    fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;
    const opener: HTMLButtonElement = host.querySelector('button[aria-label="Shopping cart"]')!;
    const previousOverflow: string = document.body.style.overflow;
    for (const action of ['backdrop', 'close']) {
      opener.focus();
      opener.click();
      fixture.detectChanges();
      await fixture.whenStable();
      const dialog: HTMLDialogElement = host.querySelector('dialog')!;
      if (action === 'backdrop') {
        const rect: DOMRect = dialog.getBoundingClientRect();
        dialog.dispatchEvent(new MouseEvent('click', {clientX: rect.left - 1, clientY: rect.top + 1}));
      } else {
        dialog.querySelector<HTMLButtonElement>('button[aria-label="Close"]')!.click();
      }
      fixture.detectChanges();
      expect(host.querySelector('dialog')).toBeNull();
      expect(opener.getAttribute('aria-expanded')).toBe('false');
      expect(document.activeElement).toBe(opener);
      expect(document.body.style.overflow).toBe(previousOverflow);
    }
    fixture.destroy();
  });

  it('restores the cart opener on Escape and Continue shopping without changing cart contents', async () => {
    const item: CartItem = {cartItemId: 'line-1', quantity: 1, product: {
      id: 'vase', label: 'Vase', originalPrice: 20, markedPrice: 20, stockQuantity: 10,
      categoryId: 'ceramics', availablePersonalizations: [], tags: [], images: [],
    }};
    TestBed.overrideProvider(CartService, {useValue: {
      getCartCount: (): Observable<number> => of(1),
      getCartItems: (): Observable<CartItem[]> => of([item]),
      getCartTotal: (): Observable<number> => of(20),
    }});
    const fixture: ComponentFixture<TopMenuComponent> = TestBed.createComponent(TopMenuComponent);
    fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;
    const opener: HTMLButtonElement = host.querySelector('button[aria-label="Shopping cart"]')!;
    for (const action of ['escape', 'continue']) {
      opener.focus();
      opener.click();
      fixture.detectChanges();
      await fixture.whenStable();
      const dialog: HTMLDialogElement = host.querySelector('dialog')!;
      if (action === 'escape') dialog.dispatchEvent(new Event('cancel', {cancelable: true}));
      else dialog.querySelector<HTMLButtonElement>('.cart-continue')!.click();
      fixture.detectChanges();
      expect(host.querySelector('dialog')).toBeNull();
      expect(document.activeElement).toBe(opener);
      expect(item.quantity).toBe(1);
    }
    const home: HTMLAnchorElement = host.querySelector('a[href="/dashboard"]')!;
    home.focus();
    document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));
    expect(document.activeElement).toBe(home);
    fixture.destroy();
  });
});
