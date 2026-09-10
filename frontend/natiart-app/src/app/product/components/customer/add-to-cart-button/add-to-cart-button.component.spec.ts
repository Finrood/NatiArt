import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AddToCartButtonComponent } from './add-to-cart-button.component';
import { Product } from '../../../models/product.model';
import { PersonalizationOption } from '../../../models/support/personalization-option';
import { ProductService } from '../../../service/product.service';
import { CartService } from '../../../service/cart.service';

describe('AddToCartButtonComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddToCartButtonComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(AddToCartButtonComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('revalidates the product before adding a personalized line', () => {
    const fixture = TestBed.createComponent(AddToCartButtonComponent);
    const component: AddToCartButtonComponent = fixture.componentInstance;
    const productService: ProductService = TestBed.inject(ProductService);
    const cartService: CartService = TestBed.inject(CartService);
    const staleProduct: Product = {
      id: 'p1', label: 'Painting', originalPrice: 100, markedPrice: 80, stockQuantity: 4,
      categoryId: 'cat-1', availablePersonalizations: [PersonalizationOption.GOLDEN_BORDER], tags: new Set<string>(), images: []
    };
    const currentProduct: Product = {...staleProduct, markedPrice: 95, stockQuantity: 2};
    const getProductSpy: jasmine.Spy = spyOn(productService, 'getProduct').and.returnValue(of(currentProduct));
    const addToCartSpy: jasmine.Spy = spyOn(cartService, 'addToCart').and.returnValue(of(undefined));
    component.product = staleProduct;

    component.addToCartOrPersonalize(staleProduct, {currentTarget: document.createElement('button')} as unknown as MouseEvent);
    component.onPersonalizationComplete({goldBorder: true});

    expect(getProductSpy).toHaveBeenCalledWith('p1');
    expect(addToCartSpy).toHaveBeenCalledWith(currentProduct, 1, true, undefined);
  });
});
