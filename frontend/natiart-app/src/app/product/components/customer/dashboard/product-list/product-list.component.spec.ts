import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpRequest } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

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
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
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
      tags: new Set<string>(),
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
      tags: new Set<string>(),
      images: [],
    };

    const button: HTMLElement = document.createElement('button');
    const event: MouseEvent = { currentTarget: button } as unknown as MouseEvent;
    component.addToCart(product, event);
    expect(component.showPersonalizationModal).toBeTrue();
    expect(component.selectedProduct).toBe(product);
    expect(addSpy).not.toHaveBeenCalled();
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
      tags: new Set<string>(),
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
});
