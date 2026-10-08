import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpRequest } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ProductListComponent } from './product-list.component';
import { Product } from '../../../../models/product.model';
import { PersonalizationOption } from '../../../../models/support/personalization-option';
import { CartService } from '../../../../service/cart.service';
import { ProductService } from '../../../../service/product.service';

describe('ProductListComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach((): void => {
    httpMock.verify();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ProductListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('limits the home edit and replaces excluded pieces without requesting hidden artwork', () => {
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.componentRef.setInput('limit', 2);
    fixture.componentRef.setInput('excludeProductIds', ['p-1']);
    const service: ProductService = TestBed.inject(ProductService);
    const imageSpy: jasmine.Spy = spyOn(service, 'getImage').and.returnValue(of(new Blob(['image'])));
    const products: Product[] = [1, 2, 3, 4].map((id: number): Product => ({
      id: 'p-' + id, label: 'Piece ' + id, originalPrice: 10, markedPrice: 10,
      stockQuantity: 2, categoryId: 'category', availablePersonalizations: [], tags: [], images: ['image-' + id]
    }));
    fixture.detectChanges();
    httpMock.expectOne((req: HttpRequest<unknown>): boolean => req.url.includes('/featured')).flush(products);
    expect(fixture.componentInstance.products.value.map((product: Product): string | undefined => product.id)).toEqual(['p-2', 'p-3']);
    expect(imageSpy.calls.allArgs()).toEqual([['image-2'], ['image-3']]);
    fixture.componentRef.setInput('excludeProductIds', ['p-2']);
    fixture.detectChanges();
    expect(fixture.componentInstance.products.value.map((product: Product): string | undefined => product.id)).toEqual(['p-1', 'p-3']);
    expect(fixture.componentInstance.imageUrls['p-2']).toBeUndefined();
    expect(imageSpy).toHaveBeenCalledTimes(3);
    fixture.destroy();
  });

  it('defers lower-page image bytes until the section approaches the viewport and disconnects on destroy', () => {
    let intersect: IntersectionObserverCallback | undefined;
    const disconnect: jasmine.Spy = jasmine.createSpy('disconnect');
    const observer: IntersectionObserver = {observe: jasmine.createSpy('observe'), disconnect} as unknown as IntersectionObserver;
    spyOn(window, 'IntersectionObserver').and.callFake(function(callback: IntersectionObserverCallback): IntersectionObserver {
      intersect = callback; return observer;
    });
    const fixture = TestBed.createComponent(ProductListComponent);
    fixture.componentRef.setInput('deferImages', true);
    const service: ProductService = TestBed.inject(ProductService);
    const imageSpy: jasmine.Spy = spyOn(service, 'getImage').and.returnValue(of(new Blob(['image'])));
    fixture.detectChanges();
    httpMock.expectOne((req: HttpRequest<unknown>): boolean => req.url.includes('/featured')).flush([{
      id: 'p-1', label: 'Piece', originalPrice: 10, markedPrice: 10, stockQuantity: 2,
      categoryId: 'category', availablePersonalizations: [], tags: [], images: ['image-1']
    }]);
    expect(imageSpy).not.toHaveBeenCalled();
    intersect?.([{isIntersecting: true} as IntersectionObserverEntry], observer);
    expect(imageSpy).toHaveBeenCalledOnceWith('image-1');
    fixture.destroy();
    expect(disconnect).toHaveBeenCalled();
  });

  it('skips image fetch and direct-adds id-less products without throwing (AA1)', () => {
    const fixture = TestBed.createComponent(ProductListComponent);
    const component: ProductListComponent = fixture.componentInstance;
    const productService: ProductService = TestBed.inject(ProductService);
    const cartService: CartService = TestBed.inject(CartService);
    const getImageSpy: jasmine.Spy = spyOn(productService, 'getImage').and.returnValue(of(new Blob()));
    const addSpy: jasmine.Spy = spyOn(cartService, 'addToCart').and.returnValue(of(undefined));

    const bareProduct: Product = {
      label: 'Bare',
      originalPrice: 10,
      markedPrice: 8,
      stockQuantity: 3,
      categoryId: 'cat-1',
      availablePersonalizations: undefined as unknown as PersonalizationOption[],
      tags: [],
      images: ['img/a.png'],
    };

    component.ngOnInit();
    const listReq = httpMock.expectOne((req: HttpRequest<unknown>): boolean => req.url.indexOf('/featured') !== -1);
    listReq.flush([bareProduct]);
    expect(getImageSpy).not.toHaveBeenCalled();

    const button: HTMLElement = document.createElement('button');
    const event: MouseEvent = { currentTarget: button } as unknown as MouseEvent;
    expect((): void => component.addToCart(bareProduct, event)).not.toThrow();
    expect(addSpy).toHaveBeenCalled();
  });

  it('opens the personalization modal when golden-border is offered (AA1)', () => {
    const fixture = TestBed.createComponent(ProductListComponent);
    const component: ProductListComponent = fixture.componentInstance;
    const cartService: CartService = TestBed.inject(CartService);
    const addSpy: jasmine.Spy = spyOn(cartService, 'addToCart').and.returnValue(of(undefined));

    const product: Product = {
      id: 'p-1',
      label: 'Fancy',
      originalPrice: 20,
      markedPrice: 18,
      stockQuantity: 5,
      categoryId: 'cat-1',
      availablePersonalizations: [PersonalizationOption.GOLDEN_BORDER],
      tags: [],
      images: [],
    };

    const button: HTMLElement = document.createElement('button');
    const event: MouseEvent = { currentTarget: button } as unknown as MouseEvent;
    component.addToCart(product, event);
    expect(component.showPersonalizationModal).toBeTrue();
    expect(component.selectedProduct).toBe(product);
    expect(addSpy).not.toHaveBeenCalled();
  });

  it('issues zero image GETs on a repeat emission for already loading/loaded lines (AA4)', () => {
    const fixture = TestBed.createComponent(ProductListComponent);
    const component: ProductListComponent = fixture.componentInstance;
    const productService: ProductService = TestBed.inject(ProductService);
    const getImageSpy: jasmine.Spy = spyOn(productService, 'getImage').and.returnValue(of(new Blob(['img'])));

    const product: Product = {
      id: 'p-1',
      label: 'Card',
      originalPrice: 10,
      markedPrice: 8,
      stockQuantity: 3,
      categoryId: 'cat-1',
      availablePersonalizations: [],
      tags: [],
      images: ['img/a.png'],
    };

    component.ngOnInit();
    const listReq = httpMock.expectOne((req: HttpRequest<unknown>): boolean => req.url.indexOf('/featured') !== -1);
    listReq.flush([product]);
    expect(getImageSpy).toHaveBeenCalledTimes(1);

    const internals = component as unknown as {
      updateProductImages(products: Product[]): void;
    };
    internals.updateProductImages([product]);
    expect(getImageSpy).toHaveBeenCalledTimes(1);
    expect(component.imageUrls['p-1']).toBeTruthy();
  });

  it('falls back to the placeholder when the image GET fails (AA4)', () => {
    const fixture = TestBed.createComponent(ProductListComponent);
    const component: ProductListComponent = fixture.componentInstance;
    const productService: ProductService = TestBed.inject(ProductService);
    spyOn(productService, 'getImage').and.returnValue(throwError(() => new Error('boom')));

    const product: Product = {
      id: 'p-9',
      label: 'Broken',
      originalPrice: 10,
      markedPrice: 8,
      stockQuantity: 3,
      categoryId: 'cat-1',
      availablePersonalizations: [],
      tags: [],
      images: ['img/missing.png'],
    };

    component.ngOnInit();
    const listReq = httpMock.expectOne((req: HttpRequest<unknown>): boolean => req.url.indexOf('/featured') !== -1);
    listReq.flush([product]);
    expect(component.imageUrls['p-9']).toBe(component.emptyImage);
  });

  it('preserves card DOM nodes across same-id re-emissions (AF1)', () => {
    const fixture = TestBed.createComponent(ProductListComponent);
    const component: ProductListComponent = fixture.componentInstance;

    const makeProduct = (suffix: string): Product => ({
      id: 'p-' + suffix,
      label: 'Product ' + suffix,
      originalPrice: 10,
      markedPrice: 8,
      stockQuantity: 3,
      categoryId: 'cat-1',
      availablePersonalizations: [],
      tags: [],
      images: [],
    });

    fixture.detectChanges();
    const listReq = httpMock.expectOne((req: HttpRequest<unknown>): boolean => req.url.indexOf('/featured') !== -1);
    listReq.flush([makeProduct('1'), makeProduct('2')]);
    fixture.detectChanges();

    const cardsBefore: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.product-card'));
    expect(cardsBefore.length).toBe(2);

    component.products.next([makeProduct('1'), makeProduct('2')]);
    fixture.detectChanges();

    const cardsAfter: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.product-card'));
    expect(cardsAfter.length).toBe(2);
    expect(cardsAfter[0]).toBe(cardsBefore[0]);
    expect(cardsAfter[1]).toBe(cardsBefore[1]);
  });

  it('prunes image urls and revokes the blob when a product leaves the listing (AS2)', () => {
    const fixture = TestBed.createComponent(ProductListComponent);
    const component: ProductListComponent = fixture.componentInstance;
    const productService: ProductService = TestBed.inject(ProductService);
    spyOn(productService, 'getImage').and.returnValue(of(new Blob(['img'])));
    const revokeSpy: jasmine.Spy = spyOn(URL, 'revokeObjectURL');

    const makeProduct = (suffix: string): Product => ({
      id: 'p-' + suffix,
      label: 'Product ' + suffix,
      originalPrice: 10,
      markedPrice: 8,
      stockQuantity: 3,
      categoryId: 'cat-1',
      availablePersonalizations: [],
      tags: [],
      images: ['img/' + suffix + '.png'],
    });

    component.ngOnInit();
    const listReq = httpMock.expectOne((req: HttpRequest<unknown>): boolean => req.url.indexOf('/featured') !== -1);
    listReq.flush([makeProduct('1'), makeProduct('2')]);
    expect(Object.keys(component.imageUrls).length).toBe(2);

    const internals = component as unknown as {
      updateProductImages(products: Product[]): void;
    };
    internals.updateProductImages([makeProduct('1')]);

    expect(component.imageUrls['p-2']).toBeUndefined();
    expect(revokeSpy).toHaveBeenCalled();
  });
});
