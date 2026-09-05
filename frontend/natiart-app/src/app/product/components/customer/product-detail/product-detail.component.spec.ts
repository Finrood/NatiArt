import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { ProductDetailComponent } from './product-detail.component';
import { ProductService } from '../../../service/product.service';
import { CartService } from '../../../service/cart.service';
import { Product } from '../../../models/product.model';

function makeProduct(id: string): Product {
  return {
    id,
    label: `Product ${id}`,
    originalPrice: 100,
    markedPrice: 80,
    stockQuantity: 10,
    categoryId: 'cat-1',
    availablePersonalizations: [],
    tags: new Set<string>(),
    images: [],
  };
}

describe('ProductDetailComponent', () => {
  let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let getProduct: jasmine.Spy;
  let fixture: ComponentFixture<ProductDetailComponent>;
  let component: ProductDetailComponent;

  beforeEach(async () => {
    paramMap$ = new BehaviorSubject(convertToParamMap({ id: 'p1' }));
    getProduct = jasmine.createSpy('getProduct').and.callFake((id: string) => of(makeProduct(id)));

    await TestBed.configureTestingModule({
      imports: [ProductDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimations(),
        { provide: ActivatedRoute, useValue: { paramMap: paramMap$.asObservable() } },
        {
          provide: ProductService,
          useValue: {
            getProduct,
            getProductsByCategory: () => of([]),
            getImage: () => of(new Blob()),
          },
        },
        { provide: CartService, useValue: { addToCart: () => undefined, getCartCount: () => of(0) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads the routed product on init', () => {
    expect(getProduct).toHaveBeenCalledWith('p1');
    expect(component.product$.value?.id).toBe('p1');
    expect(component.isLoading).toBe(false);
    expect(component.loadError).toBeNull();
  });

  it('reloads when the route id changes and resets per-product state', () => {
    component.quantity = 3;
    component.selectedImageIndex = 2;
    paramMap$.next(convertToParamMap({ id: 'p2' }));
    expect(getProduct).toHaveBeenCalledWith('p2');
    expect(component.product$.value?.id).toBe('p2');
    expect(component.quantity).toBe(1);
    expect(component.selectedImageIndex).toBe(0);
    expect(component.isLoading).toBe(false);
  });

  it('surfaces an error state instead of loading forever on fetch failure', () => {
    getProduct.and.returnValue(throwError(() => new Error('boom')));
    paramMap$.next(convertToParamMap({ id: 'p9' }));
    expect(component.product$.value).toBeNull();
    expect(component.loadError).toBe('Could not load this product. Please try again.');
    expect(component.isLoading).toBe(false);
  });

  it('surfaces an error state without HTTP when the id param is missing', () => {
    getProduct.calls.reset();
    paramMap$.next(convertToParamMap({}));
    expect(getProduct).not.toHaveBeenCalled();
    expect(component.product$.value).toBeNull();
    expect(component.loadError).toBe('Could not load this product. Please try again.');
    expect(component.isLoading).toBe(false);
  });
});

describe('ProductDetailComponent stale main images', () => {
  let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let imageSubjects: Map<string, BehaviorSubject<Blob>>;

  function makeImagedProduct(id: string): Product {
    return {...makeProduct(id), images: [`img-${id}`]};
  }

  beforeEach(async () => {
    paramMap$ = new BehaviorSubject(convertToParamMap({id: 'p1'}));
    imageSubjects = new Map<string, BehaviorSubject<Blob>>();

    await TestBed.configureTestingModule({
      imports: [ProductDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimations(),
        {provide: ActivatedRoute, useValue: {paramMap: paramMap$.asObservable()}},
        {
          provide: ProductService,
          useValue: {
            getProduct: (id: string) => of(makeImagedProduct(id)),
            getProductsByCategory: () => of([]),
            getImage: (path: string) => {
              if (!imageSubjects.has(path)) {
                imageSubjects.set(path, new BehaviorSubject<Blob>(new Blob()));
              }
              return imageSubjects.get(path)!.asObservable();
            },
          },
        },
        {provide: CartService, useValue: {addToCart: () => undefined, getCartCount: () => of(0)}},
      ],
    }).compileComponents();
  });

  it('drops a previous product image that resolves after navigating away', () => {
    const fixture = TestBed.createComponent(ProductDetailComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    expect(component.product$.value?.id).toBe('p1');

    // Navigate to p2 before p1's image resolves; the p1 fetch stays in flight.
    paramMap$.next(convertToParamMap({id: 'p2'}));
    expect(component.product$.value?.id).toBe('p2');

    // Late p1 resolution must not populate the reset index-keyed map.
    imageSubjects.get('img-p1')!.next(new Blob(['p1-bytes']));
    expect(component.imageUrls[0]).toBeUndefined();

    // The current product image still loads normally.
    imageSubjects.get('img-p2')!.next(new Blob(['p2-bytes']));
    expect(component.imageUrls[0]).toBeTruthy();
    component.ngOnDestroy();
  });
});
