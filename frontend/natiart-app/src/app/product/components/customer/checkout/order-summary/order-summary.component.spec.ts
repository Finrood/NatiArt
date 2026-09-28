import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import { OrderSummaryComponent } from './order-summary.component';
import { ProductService } from '../../../../service/product.service';
import { Product } from '../../../../models/product.model';
import { ShippingQuote } from '../../../../service/shipping.service';

describe('OrderSummaryComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderSummaryComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(OrderSummaryComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('displays server item, shipping, and total amounts when a quote is present', () => {
    const fixture = TestBed.createComponent(OrderSummaryComponent);
    const component = fixture.componentInstance;
    const product: Product = {
      id: 'p1', label: 'Plate', originalPrice: 100, markedPrice: 90,
      stockQuantity: 5, categoryId: 'c1', availablePersonalizations: [],
      tags: new Set<string>(), images: [],
    };
    const quote: ShippingQuote = {
      quoteId: 'quote-1', destinationPostalCode: '01001000', serviceId: 'pac', serviceName: 'PAC',
      expiresAt: '2099-01-01T00:00:00Z', itemAmount: 80, shippingAmount: 12.5, totalAmount: 92.5,
      items: [{productId: 'p1', quantity: 1, unitPrice: 80, lineAmount: 80}],
    };
    component.cartItems = [{cartItemId: 'line-1', product, quantity: 1}];
    component.shippingQuote = quote;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('R$80.00');
    expect(fixture.nativeElement.textContent).toContain('R$12.50');
    expect(fixture.nativeElement.textContent).toContain('R$92.50');
    expect(fixture.nativeElement.textContent).toContain('PAC');
  });

  it('matches quoted prices to personalized lines of the same product', () => {
    const component = TestBed.createComponent(OrderSummaryComponent).componentInstance;
    const product: Product = {
      id: 'p1', label: 'Plate', originalPrice: 10, markedPrice: 10,
      stockQuantity: 5, categoryId: 'c1', availablePersonalizations: [],
      tags: new Set<string>(), images: [],
    };
    const uploadId = '2b7f4d7e-6e55-4a8f-a8b2-f2b7069e4d2c';
    component.shippingQuote = {
      quoteId: 'q1', destinationPostalCode: '01001000', serviceId: 'pac', serviceName: 'PAC',
      expiresAt: '2099-01-01T00:00:00Z', itemAmount: 37.5, shippingAmount: 8, totalAmount: 45.5,
      items: [
        {productId: 'p1', personalizationKey: `CUSTOM_IMAGE=${uploadId}`, quantity: 2, unitPrice: 12.5, lineAmount: 25},
        {productId: 'p1', personalizationKey: 'GOLDEN_BORDER=true', quantity: 1, unitPrice: 12.5, lineAmount: 12.5},
      ],
    };
    const golden = {cartItemId: 'gold', product, quantity: 1, goldBorder: true};
    const artwork = {cartItemId: 'art', product, quantity: 2, customImageUploadId: uploadId};

    expect(component.getDisplayedItemUnitPrice(golden)).toBe(12.5);
    expect(component.getItemAmount(golden)).toBe(12.5);
    expect(component.getItemAmount(artwork)).toBe(25);
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
