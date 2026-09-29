import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { ProductManagementComponent } from './admin-product-management.component';
import { ProductService } from '../../../service/product.service';
import { CategoryService } from '../../../service/category.service';
import { PackageService } from '../../../service/package.service';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';
import { ImageService } from '../../../service/image.service';
import { Product } from '../../../models/product.model';

describe('ProductManagementComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductManagementComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
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


describe('ProductManagementComponent ordered image sessions', () => {
  let imageRequests: Map<string, Subject<Blob>>;
  let productService: {getProducts: jasmine.Spy; getImage: jasmine.Spy; updateProduct: jasmine.Spy};
  const product = (id: string, images: string[]): Product => ({id, images, label: 'Art', originalPrice: 10,
    markedPrice: 10, stockQuantity: 10, categoryId: 'cat', tags: new Set<string>(), availablePersonalizations: []});

  beforeEach(async () => {
    imageRequests = new Map<string, Subject<Blob>>();
    productService = {getProducts: jasmine.createSpy().and.returnValue(of([])),
      getImage: jasmine.createSpy().and.callFake((path: string): Subject<Blob> => {
        const request: Subject<Blob> = new Subject<Blob>();
        imageRequests.set(path, request);
        return request;
      }), updateProduct: jasmine.createSpy().and.returnValue(new Subject<Product>())};
    await TestBed.configureTestingModule({imports: [ProductManagementComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      {provide: ProductService, useValue: productService},
      {provide: CategoryService, useValue: {getCategories: (): unknown => of([])}},
      {provide: PackageService, useValue: {getPackages: (): unknown => of([])}},
    ]}).compileComponents();
  });

  it('uses stable IDs after reorder and cancels removed or old-session previews', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    component.openModal(product('A', ['a', 'b']));
    component.onImageDrop({previousIndex: 0, currentIndex: 1} as Parameters<ProductManagementComponent['onImageDrop']>[0]);
    imageRequests.get('a')!.next(new Blob(['A']));
    expect(component.imagePreviews.map(preview => preview.id)).toEqual(['b', 'a']);
    expect(component.imagePreviews[1].objectUrl).toBeTruthy();
    component.removeImage('a');
    expect(revoke).toHaveBeenCalled();
    expect(imageRequests.get('a')!.observed).toBeFalse();
    const pendingB: Subject<Blob> = imageRequests.get('b')!;
    component.closeModal();
    component.openModal(product('B', ['c']));
    expect(pendingB.observed).toBeFalse();
    pendingB.next(new Blob(['old B']));
    expect(component.imagePreviews.map(preview => preview.id)).toEqual(['c']);
    fixture.destroy();
  });

  it('submits the exact interleaved manifest and correlates new files by stable upload ID', async () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.openModal(product('A', ['a', 'b']));
    const input: HTMLInputElement = document.createElement('input');
    input.type = 'file';
    const files: DataTransfer = new DataTransfer();
    files.items.add(new File(['image'], 'new.png', {type: 'image/png'}));
    input.files = files.files;
    await component.onFileSelected({target: input} as unknown as Event);
    const uploadId: string = component.imagePreviews[2].id;
    component.onImageDrop({previousIndex: 2, currentIndex: 1} as Parameters<ProductManagementComponent['onImageDrop']>[0]);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', {bubbles: true, cancelable: true}));
    const form: FormData = productService.updateProduct.calls.mostRecent().args[1] as FormData;
    const dto = JSON.parse(await (form.get('productDto') as Blob).text());
    expect(dto.imageManifest).toEqual([{existingImage: 'a'}, {uploadId}, {existingImage: 'b'}]);
    expect((form.get('newImages') as File).name).toBe(`${uploadId}.webp`);
    fixture.destroy();
  });

  it('rejects a conversion completion from a closed edit and revokes loaded previews on close', async () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.openModal(product('A', ['a']));
    const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    imageRequests.get('a')!.next(new Blob(['A']));
    let resolve!: (file: File) => void;
    spyOn(TestBed.inject(ImageService), 'convertFile').and.returnValue(new Promise<File>((done) => resolve = done));
    const input: HTMLInputElement = document.createElement('input');
    input.type = 'file';
    const files: DataTransfer = new DataTransfer();
    const file: File = new File(['image'], 'new.png', {type: 'image/png'});
    files.items.add(file);
    input.files = files.files;
    const conversion: Promise<void> = component.onFileSelected({target: input} as unknown as Event);
    component.closeModal();
    expect(revoke).toHaveBeenCalled();
    component.openModal(product('B', ['b']));
    resolve(file);
    await conversion;
    expect(component.imagePreviews.map(preview => preview.id)).toEqual(['b']);
    fixture.destroy();
  });
});
