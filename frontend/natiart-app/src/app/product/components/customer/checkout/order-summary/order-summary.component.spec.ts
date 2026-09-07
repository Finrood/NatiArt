import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { Subject } from 'rxjs';

import { OrderSummaryComponent } from './order-summary.component';
import { ProductService } from '../../../../service/product.service';
import { Product } from '../../../../models/product.model';

describe('OrderSummaryComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderSummaryComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(OrderSummaryComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('writes the image for a live line when its GET resolves (AA3 control)', () => {
    const fixture = TestBed.createComponent(OrderSummaryComponent);
    const component = fixture.componentInstance;
    const productService: ProductService = TestBed.inject(ProductService);
    const product: Product = {
      id: 'p1', label: 'Vase', originalPrice: 100, markedPrice: 80,
      stockQuantity: 5, categoryId: 'c1', availablePersonalizations: [],
      tags: new Set<string>(), images: ['a.jpg'],
    };
    component.cartItems = [{ cartItemId: 'line-1', product, quantity: 1 }];
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
    const fixture = TestBed.createComponent(OrderSummaryComponent);
    const component = fixture.componentInstance;
    const productService: ProductService = TestBed.inject(ProductService);
    // Empty input: the line was removed while its GET was in flight.
    component.cartItems = [];
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
