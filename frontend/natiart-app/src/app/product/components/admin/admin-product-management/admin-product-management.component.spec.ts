import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { of, Subject, throwError } from 'rxjs';

import { ProductManagementComponent } from './admin-product-management.component';
import { ProductService } from '../../../service/product.service';
import { CategoryService } from '../../../service/category.service';
import { PackageService } from '../../../service/package.service';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';
import { Product } from '../../../models/product.model';

describe('ProductManagementComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductManagementComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('tracks the golden-border valueChanges subscription so destroy unsubscribes it (P1)', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    const control = component.productForm.get('hasFixedGoldenBorder');
    expect(control).toBeTruthy();
    const stream: Subject<boolean> = (control?.valueChanges as unknown) as Subject<boolean>;
    expect(stream.observed).toBeTrue();

    fixture.destroy();
    expect(stream.observed).toBeFalse();
  });

  it('revokes product image object URLs on destroy', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    const component = fixture.componentInstance;
    const productService: ProductService = TestBed.inject(ProductService);
    spyOn(productService, 'getImage').and.returnValue(of(new Blob(['x'])));
    const revokeSpy = spyOn(URL, 'revokeObjectURL');

    const fetcher = component as unknown as {
      fetchImage(productId: string, imagePath: string): void;
    };
    fetcher.fetchImage('prod-1', 'img-1');
    expect(component.imageUrls['prod-1']).toBeTruthy();

    fixture.destroy();

    expect(revokeSpy).toHaveBeenCalled();
  });
});

describe('ProductManagementComponent error UX (O2)', () => {
  let productOps: {
    getProducts: jasmine.Spy;
    getImage: jasmine.Spy;
    inverseProductVisibility: jasmine.Spy;
  };

  beforeEach(async () => {
    productOps = {
      getProducts: jasmine.createSpy('getProducts').and.returnValue(of([])),
      getImage: jasmine.createSpy('getImage').and.returnValue(of(new Blob(['x']))),
      inverseProductVisibility: jasmine.createSpy('inverseProductVisibility')
        .and.returnValue(of({ id: 'p1' })),
    };
    await TestBed.configureTestingModule({
      imports: [ProductManagementComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideAnimations(),
        { provide: ProductService, useValue: productOps },
        { provide: CategoryService, useValue: { getCategories: (): Subject<never[]> => new Subject<never[]>() } },
        { provide: PackageService, useValue: { getPackages: (): Subject<never[]> => new Subject<never[]>() } },
      ],
    }).compileComponents();
  });

  it('routes a failed product list load to an error alert (O2)', async () => {
    productOps.getProducts.and.returnValue(throwError(() => new Error('down')));

    const fixture = TestBed.createComponent(ProductManagementComponent);
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve, 10));
    const child = (fixture.componentInstance as unknown as { alertMessageComponent: AlertMessageComponent })
      .alertMessageComponent;

    expect(child.alertMessages).toEqual([{ message: 'Error loading products', type: 'error' }]);
  });

  it('routes a failed visibility toggle to an error alert (O2)', () => {
    productOps.inverseProductVisibility.and.returnValue(throwError(() => new Error('down')));
    const fixture = TestBed.createComponent(ProductManagementComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const alertOwner = component as unknown as { alertMessageComponent: AlertMessageComponent };
    const alertSpy: jasmine.Spy = spyOn(alertOwner.alertMessageComponent, 'showAlert');

    component.toggleProductVisibility({ id: 'p1' } as Product);

    expect(alertSpy).toHaveBeenCalledWith({ message: 'Error changing product visibility', type: 'error' });
  });

  it('falls back to an empty image slot when the image fetch fails (O2)', () => {
    productOps.getImage.and.returnValue(throwError(() => new Error('down')));
    const fixture = TestBed.createComponent(ProductManagementComponent);
    fixture.detectChanges();
    const fetcher = fixture.componentInstance as unknown as {
      fetchImage(productId: string, imagePath: string): void;
    };

    expect((): void => fetcher.fetchImage('p1', 'missing.png')).not.toThrow();
    expect(fixture.componentInstance.imageUrls['p1']).toBeNull();
  });
});
