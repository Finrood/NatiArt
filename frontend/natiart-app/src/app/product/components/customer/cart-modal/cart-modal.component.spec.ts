import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { BehaviorSubject, of } from 'rxjs';

import { CartModalComponent } from './cart-modal.component';
import { CartService } from '../../../service/cart.service';
import { ProductService } from '../../../service/product.service';
import { CartItem } from '../../../models/CartItem.model';
import { Product } from '../../../models/product.model';

function makeProduct(): Product {
  return {
    id: 'prod-1',
    label: 'Vase',
    originalPrice: 100,
    markedPrice: 80,
    stockQuantity: 10,
    categoryId: 'cat-1',
    availablePersonalizations: [],
    tags: new Set<string>(),
    images: []
  };
}

function makeItem(cartItemId: string): CartItem {
  return {cartItemId: cartItemId, product: makeProduct(), quantity: 1};
}

describe('CartModalComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CartModalComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(CartModalComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('updates the quantity of the targeted cart line when two lines share a product', () => {
    const fixture = TestBed.createComponent(CartModalComponent);
    const cartService: CartService = TestBed.inject(CartService);
    const updateSpy = spyOn(cartService, 'updateItemQuantity').and.returnValue(of(undefined));
    const first: CartItem = makeItem('line-1');
    const second: CartItem = makeItem('line-2');

    fixture.componentInstance.updateQuantity(first, 2);
    fixture.componentInstance.updateQuantity(second, 3);

    expect(updateSpy).toHaveBeenCalledWith('line-1', 2);
    expect(updateSpy).toHaveBeenCalledWith('line-2', 3);
  });

  it('removes the targeted cart line when two lines share a product', () => {
    const fixture = TestBed.createComponent(CartModalComponent);
    const cartService: CartService = TestBed.inject(CartService);
    const removeSpy = spyOn(cartService, 'removeFromCart').and.returnValue(of(undefined));
    const event: Event = {stopPropagation: (): void => undefined} as Event;

    fixture.componentInstance.removeItem(makeItem('line-2'), event);

    expect(removeSpy).toHaveBeenCalledWith('line-2');
    expect(removeSpy).not.toHaveBeenCalledWith('line-1');
  });

  it('fetches each cart line image once across re-emissions of the same cart', () => {
    const withImage = (item: CartItem): CartItem => ({
      ...item,
      product: {...item.product, images: ['img-1']},
    });
    const items$ = new BehaviorSubject<CartItem[]>([withImage(makeItem('line-1'))]);
    TestBed.overrideProvider(CartService, {
      useValue: {getCartItems: (): BehaviorSubject<CartItem[]> => items$, getCartTotal: () => of(0)},
    });
    const productService: ProductService = TestBed.inject(ProductService);
    const getImageSpy = spyOn(productService, 'getImage').and.returnValue(of(new Blob()));

    const fixture = TestBed.createComponent(CartModalComponent);
    fixture.detectChanges(); // ngOnInit subscribes and fetches once
    expect(getImageSpy).toHaveBeenCalledTimes(1);

    // Same cart re-emits (e.g. after a quantity update): no refetch.
    items$.next([withImage(makeItem('line-1'))]);
    expect(getImageSpy).toHaveBeenCalledTimes(1);
  });

  it('revokes created object URLs when the component is destroyed', () => {
    const withImage = (item: CartItem): CartItem => ({
      ...item,
      product: {...item.product, images: ['img-1']},
    });
    const items$ = new BehaviorSubject<CartItem[]>([withImage(makeItem('line-1'))]);
    TestBed.overrideProvider(CartService, {
      useValue: {getCartItems: (): BehaviorSubject<CartItem[]> => items$, getCartTotal: () => of(0)},
    });
    const productService: ProductService = TestBed.inject(ProductService);
    spyOn(productService, 'getImage').and.returnValue(of(new Blob(['x'])));
    const revokeSpy = spyOn(URL, 'revokeObjectURL');

    const fixture = TestBed.createComponent(CartModalComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.imageUrls['line-1']).toBeTruthy();

    fixture.destroy();

    expect(revokeSpy).toHaveBeenCalled();
  });

  it('revokes the object URL of a line removed from the cart', () => {
    const withImage = (item: CartItem): CartItem => ({
      ...item,
      product: {...item.product, images: ['img-1']},
    });
    const items$ = new BehaviorSubject<CartItem[]>([withImage(makeItem('line-1'))]);
    TestBed.overrideProvider(CartService, {
      useValue: {getCartItems: (): BehaviorSubject<CartItem[]> => items$, getCartTotal: () => of(0)},
    });
    const productService: ProductService = TestBed.inject(ProductService);
    spyOn(productService, 'getImage').and.returnValue(of(new Blob(['x'])));
    const revokeSpy = spyOn(URL, 'revokeObjectURL');

    const fixture = TestBed.createComponent(CartModalComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.imageUrls['line-1']).toBeTruthy();

    items$.next([]);

    expect(revokeSpy).toHaveBeenCalled();
    expect(fixture.componentInstance.imageUrls['line-1']).toBeUndefined();
  });
});
