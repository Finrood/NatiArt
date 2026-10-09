import {Component, provideZonelessChangeDetection} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {Location} from '@angular/common';
import {provideLocationMocks} from '@angular/common/testing';
import {Event as RouterEvent, NavigationEnd, provideRouter, Router} from '@angular/router';
import {filter, firstValueFrom, of} from 'rxjs';
import {AppComponent} from '../../../../app.component';
import {AuthenticationService} from '../../../../directory/service/authentication.service';
import {CartService} from '../../../service/cart.service';
import {Product} from '../../../models/product.model';

@Component({template: '<h1>Gallery</h1>'})
class GalleryPage {}
@Component({template: '<h1>Your Shopping Cart</h1>'})
class CartPage {}
@Component({template: '<h1>Product</h1>'})
class ProductPage {}
@Component({template: '<h1>Collections</h1>'})
class CollectionsPage {}

describe('Cart drawer navigation through the real app shell', () => {
  const product: Product = {id: 'vase', label: 'Vase', originalPrice: 20, markedPrice: 20,
    stockQuantity: 10, categoryId: 'ceramics', availablePersonalizations: [], tags: [], images: []};
  let fixture: ComponentFixture<AppComponent>;
  let router: Router;
  let cart: CartService;
  let host: HTMLElement;
  let opener: HTMLButtonElement;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideZonelessChangeDetection(),
        provideLocationMocks(),
        {provide: AuthenticationService, useValue: {isLoggedIn$: of(false), currentUser$: of(null), resetInactivityTimer: (): void => undefined}},
        provideRouter([{path: 'dashboard', component: GalleryPage}, {path: 'cart', component: CartPage},
          {path: 'product/:id', component: ProductPage}, {path: 'products', component: CollectionsPage}])],
    }).compileComponents();
    router = TestBed.inject(Router);
    router.setUpLocationChangeListener();
    cart = TestBed.inject(CartService);
    cart.addToCart(product, 1).subscribe();
    fixture = TestBed.createComponent(AppComponent);
    host = fixture.nativeElement;
    fixture.detectChanges();
    await router.navigateByUrl('/dashboard');
    await fixture.whenStable();
    opener = host.querySelector('button[aria-label="Shopping cart"]')!;
  });

  afterEach(() => {
    fixture.destroy();
    TestBed.inject(HttpTestingController).verify();
    localStorage.clear();
  });

  async function openDrawer(): Promise<HTMLDialogElement> {
    opener.focus();
    opener.click();
    await fixture.whenStable();
    const dialog: HTMLDialogElement = host.querySelector('dialog')!;
    expect(dialog.matches(':modal')).toBeTrue();
    return dialog;
  }

  it('opens without navigation, closes on Go to Cart and releases focus/scroll to the page, including same-URL navigation', async () => {
    const dialog: HTMLDialogElement = await openDrawer();
    expect(router.url).toBe('/dashboard');
    dialog.querySelector<HTMLAnchorElement>('a[href="/cart"]')!.click();
    await fixture.whenStable();
    expect(router.url).toBe('/cart');
    expect(host.querySelector('[data-cart-preview]')).toBeNull();
    expect(host.querySelector('dialog')).toBeNull();
    expect(document.body.style.overflow).not.toBe('hidden');
    expect(document.activeElement).toBe(host.querySelector('h1'));

    const samePageDrawer: HTMLDialogElement = await openDrawer();
    samePageDrawer.querySelector<HTMLAnchorElement>('a[href="/cart"]')!.click();
    await fixture.whenStable();
    expect(router.url).toBe('/cart');
    expect(host.querySelector('dialog')).toBeNull();
    expect(document.body.style.overflow).not.toBe('hidden');
    expect(cart.getCartItemsSnapshot()[0].quantity).toBe(1);
  });

  it('closes on a product link and browser Back without reappearing on the restored route', async () => {
    const dialog: HTMLDialogElement = await openDrawer();
    dialog.querySelector<HTMLAnchorElement>('a[href="/product/vase"]')!.click();
    await fixture.whenStable();
    expect(router.url).toBe('/product/vase');
    expect(host.querySelector('dialog')).toBeNull();
    expect(document.activeElement).toBe(host.querySelector('h1'));

    await openDrawer();
    const navigation: Promise<NavigationEnd> = firstValueFrom(router.events.pipe(
      filter((event: RouterEvent): event is NavigationEnd => event instanceof NavigationEnd)
    ));
    TestBed.inject(Location).back();
    await navigation;
    await fixture.whenStable();
    expect(router.url).toBe('/dashboard');
    expect(host.querySelector('dialog')).toBeNull();
    expect(document.body.style.overflow).not.toBe('hidden');
    expect(document.activeElement).toBe(host.querySelector('h1'));
  });

  it('gives an empty cart a real Collections link that dismisses the drawer', async () => {
    cart.clearCart().subscribe();
    const dialog: HTMLDialogElement = await openDrawer();
    expect(dialog.textContent).toContain('Your cart is empty.');
    expect(dialog.querySelector('[data-cart-subtotal]')).toBeNull();
    dialog.querySelector<HTMLAnchorElement>('a[href="/products"]')!.click();
    await fixture.whenStable();
    expect(router.url).toBe('/products');
    expect(host.querySelector('dialog')).toBeNull();
    expect(document.activeElement).toBe(host.querySelector('h1'));
  });

  it('updates quantities and subtotal inside the open drawer and preserves the selected line on dismissal', async () => {
    const dialog: HTMLDialogElement = await openDrawer();
    dialog.querySelector<HTMLButtonElement>('button[aria-label="Increase quantity for Vase"]')!.click();
    await fixture.whenStable();
    expect(cart.getCartItemsSnapshot()[0].quantity).toBe(2);
    expect(dialog.querySelector('[data-cart-subtotal]')!.textContent).toContain('40.00');
    const input: HTMLInputElement = dialog.querySelector('input')!;
    input.value = '1.5';
    input.dispatchEvent(new Event('change', {bubbles: true}));
    await fixture.whenStable();
    expect(input.value).toBe('2');
    expect(cart.getCartItemsSnapshot()[0].quantity).toBe(2);
    dialog.querySelector<HTMLButtonElement>('.cart-continue')!.click();
    await fixture.whenStable();
    expect(host.querySelector('dialog')).toBeNull();
    expect(document.activeElement).toBe(opener);
    expect(cart.getCartItemsSnapshot()[0].quantity).toBe(2);
  });
});
