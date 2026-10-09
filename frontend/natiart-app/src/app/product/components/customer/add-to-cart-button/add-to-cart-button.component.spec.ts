import {By} from '@angular/platform-browser';
import {PersonalizationModalComponent} from '../personalization-modal/personalization-modal.component';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
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
      categoryId: 'cat-1', availablePersonalizations: [PersonalizationOption.GOLDEN_BORDER], tags: [], images: []
    };
    const currentProduct: Product = {...staleProduct, markedPrice: 95, stockQuantity: 2};
    const getProductSpy: jasmine.Spy = spyOn(productService, 'getProduct').and.returnValue(of(currentProduct));
    spyOn(cartService, 'getCartItemsSnapshot').and.returnValue([]);
    const addToCartSpy: jasmine.Spy = spyOn(cartService, 'addToCart').and.returnValue(of(undefined));
    component.product = staleProduct;

    component.addToCartOrPersonalize(staleProduct, {currentTarget: document.createElement('button')} as unknown as MouseEvent);
    component.onPersonalizationComplete({goldBorder: true});

    expect(getProductSpy).toHaveBeenCalledWith('p1');
    expect(addToCartSpy).toHaveBeenCalledWith(currentProduct, 1, true, undefined);
  });
});

describe('Personalization request recovery', () => {
  let http: HttpTestingController;
  let savedCart: string | null;
  const product: Product = {id: 'atelier-recovery', label: 'Portrait plate', originalPrice: 100, markedPrice: 90,
    stockQuantity: 3, categoryId: 'c', tags: [], images: [],
    availablePersonalizations: [PersonalizationOption.GOLDEN_BORDER, PersonalizationOption.CUSTOM_IMAGE]};

  beforeEach(async () => {
    savedCart = localStorage.getItem('natiart-cart');
    await TestBed.configureTestingModule({imports: [AddToCartButtonComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]}).compileComponents();
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(CartService).clearCart();
  });
  afterEach(() => {
    http.verify();
    if (savedCart === null) localStorage.removeItem('natiart-cart');
    else localStorage.setItem('natiart-cart', savedCart);
  });

  it('retains artwork and options after failure, prevents duplicate requests and announces only an accepted line', () => {
    const fixture = TestBed.createComponent(AddToCartButtonComponent);
    fixture.componentInstance.product = product;
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('button')!.textContent).toContain('Choose options');
    root.querySelector('button')!.click(); fixture.detectChanges();
    const modal: PersonalizationModalComponent = fixture.debugElement.query(By.directive(PersonalizationModalComponent)).componentInstance;
    const artwork: File = new File(['local-artwork'], 'portrait.png', {type: 'image/png'});
    modal.goldBorder = true; modal.customImage = artwork;
    modal.onSubmit(); modal.onSubmit(); fixture.detectChanges();
    const first = http.expectOne((request): boolean => request.url.endsWith('/products/atelier-recovery'));
    const adding: HTMLButtonElement | undefined = Array.from(root.querySelectorAll<HTMLButtonElement>('dialog button')).find((button: HTMLButtonElement): boolean => button.textContent?.includes('Adding...') ?? false);
    expect(adding?.disabled).toBeTrue();
    first.flush(null, {status: 503, statusText: 'Unavailable'}); fixture.detectChanges();
    expect(root.textContent).toContain('Your options are kept');
    expect(modal.customImage).toBe(artwork); expect(modal.goldBorder).toBeTrue();
    expect(root.querySelector('dialog')).not.toBeNull();
    modal.onSubmit(); fixture.detectChanges();
    http.expectOne((request): boolean => request.url.endsWith('/products/atelier-recovery')).flush({...product, markedPrice: 95});
    fixture.detectChanges();
    expect(root.querySelector('dialog')).toBeNull();
    expect(root.querySelector('[role="status"]')!.textContent).toContain('Added to cart');
    expect(root.querySelector('a[href="/cart"]')).not.toBeNull();
    const item = TestBed.inject(CartService).getCartItemsSnapshot()[0];
    expect(item.image).toBe(artwork); expect(item.goldBorder).toBeTrue(); expect(item.product.markedPrice).toBe(95);
    fixture.destroy();
  });

  it('cancels a pending refresh on dismissal and does not add a line afterward', () => {
    const fixture = TestBed.createComponent(AddToCartButtonComponent);
    fixture.componentInstance.product = product; fixture.detectChanges();
    fixture.componentInstance.openPersonalizationModal(product); fixture.detectChanges();
    fixture.componentInstance.onPersonalizationComplete({});
    const pending = http.expectOne((request): boolean => request.url.endsWith('/products/atelier-recovery'));
    fixture.componentInstance.closePersonalizationModal(); fixture.detectChanges();
    expect(pending.cancelled).toBeTrue(); expect(fixture.componentInstance.isAdding).toBeFalse();
    expect(TestBed.inject(CartService).getCartItemsSnapshot()).toEqual([]);
    fixture.destroy();
  });

  it('does not show a success message when all available stock is already in the cart', () => {
    const fixture = TestBed.createComponent(AddToCartButtonComponent);
    const plain: Product = {...product, availablePersonalizations: []};
    fixture.componentInstance.product = plain;
    TestBed.inject(CartService).addToCart(plain, 3);
    fixture.detectChanges(); (fixture.nativeElement as HTMLElement).querySelector('button')!.click(); fixture.detectChanges();
    expect(fixture.componentInstance.$added()).toBeFalse();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Review your cart');
    expect(TestBed.inject(CartService).getCartItemsSnapshot()[0].quantity).toBe(3);
    fixture.destroy();
  });

  it('keeps artwork and the requested quantity when fresh stock cannot fulfill it', () => {
    const fixture = TestBed.createComponent(AddToCartButtonComponent);
    fixture.componentInstance.product = product;
    fixture.componentInstance.quantity = 3;
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    root.querySelector('button')!.click(); fixture.detectChanges();
    const modal: PersonalizationModalComponent = fixture.debugElement.query(By.directive(PersonalizationModalComponent)).componentInstance;
    const artwork: File = new File(['artwork'], 'portrait.png', {type: 'image/png'});
    modal.goldBorder = true; modal.customImage = artwork; modal.onSubmit();
    http.expectOne((request): boolean => request.url.endsWith('/products/atelier-recovery')).flush({...product, stockQuantity: 2});
    fixture.detectChanges();
    expect(root.querySelector('dialog')).not.toBeNull();
    expect(root.textContent).toContain('Review your cart');
    expect(modal.customImage).toBe(artwork);
    expect(fixture.componentInstance.quantity).toBe(3);
    expect(TestBed.inject(CartService).getCartItemsSnapshot()).toEqual([]);
    expect(fixture.componentInstance.$added()).toBeFalse();
    fixture.destroy();
  });

  it('does not silently add only part of a requested quantity already partly present in the cart', () => {
    const fixture = TestBed.createComponent(AddToCartButtonComponent);
    const plain: Product = {...product, availablePersonalizations: []};
    fixture.componentInstance.product = plain; fixture.componentInstance.quantity = 2;
    TestBed.inject(CartService).addToCart(plain, 2);
    fixture.detectChanges(); (fixture.nativeElement as HTMLElement).querySelector('button')!.click(); fixture.detectChanges();
    expect(fixture.componentInstance.$added()).toBeFalse();
    expect(TestBed.inject(CartService).getCartItemsSnapshot()[0].quantity).toBe(2);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Review your cart');
    fixture.destroy();
  });
});
