import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import { CartComponent } from './cart.component';
import { CartService } from '../../../service/cart.service';
import { ProductService } from '../../../service/product.service';
import { CartItem } from '../../../models/CartItem.model';
import { Product } from '../../../models/product.model';

describe('CartComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CartComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(CartComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('cancels the pending error dismissal on destroy (P2)', () => {
    const fixture = TestBed.createComponent(CartComponent);
    const component = fixture.componentInstance;
    const internals = component as unknown as {
      setError(message: string | null): void;
      errorDismissTimer: ReturnType<typeof setTimeout> | undefined;
    };
    const clearSpy: jasmine.Spy = spyOn(window, 'clearTimeout').and.callThrough();

    internals.setError('boom');
    expect(internals.errorDismissTimer).toBeDefined();

    fixture.destroy();
    expect(clearSpy).toHaveBeenCalled();
    expect(internals.errorDismissTimer).toBeUndefined();
  });

  it('writes the image for a live line when its GET resolves (AA3 control)', () => {
    const fixture = TestBed.createComponent(CartComponent);
    const component = fixture.componentInstance;
    const cartService: CartService = TestBed.inject(CartService);
    const productService: ProductService = TestBed.inject(ProductService);
    const product: Product = {
      id: 'p1', label: 'Vase', originalPrice: 100, markedPrice: 80,
      stockQuantity: 5, categoryId: 'c1', availablePersonalizations: [],
      tags: new Set<string>(), images: ['a.jpg'],
    };
    const item: CartItem = { cartItemId: 'line-1', product, quantity: 1 };
    spyOn(cartService, 'getCartItemsSnapshot').and.returnValue([item]);
    const image$: Subject<Blob> = new Subject<Blob>();
    spyOn(productService, 'getImage').and.returnValue(image$.asObservable());
    const internals = component as unknown as {
      fetchProductImage(cartItemId: string, imagePath: string): void;
    };

    internals.fetchProductImage('line-1', 'a.jpg');
    image$.next(new Blob(['x'], { type: 'image/png' }));

    expect(component.imageUrls['line-1']).toBeDefined();
  });

  it('never resurrects a removed line when its image GET resolves late (AA3)', () => {
    const fixture = TestBed.createComponent(CartComponent);
    const component = fixture.componentInstance;
    const cartService: CartService = TestBed.inject(CartService);
    const productService: ProductService = TestBed.inject(ProductService);
    // Empty snapshot: the line was removed while its GET was in flight.
    spyOn(cartService, 'getCartItemsSnapshot').and.returnValue([]);
    const image$: Subject<Blob> = new Subject<Blob>();
    spyOn(productService, 'getImage').and.returnValue(image$.asObservable());
    const internals = component as unknown as {
      fetchProductImage(cartItemId: string, imagePath: string): void;
    };

    internals.fetchProductImage('gone-line', 'a.jpg');
    image$.next(new Blob(['x'], { type: 'image/png' }));
    image$.complete();

    expect(component.imageUrls['gone-line']).toBeUndefined();
  });
});
