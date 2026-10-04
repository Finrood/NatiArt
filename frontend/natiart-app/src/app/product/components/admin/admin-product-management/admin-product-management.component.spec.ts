import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, Subscription, throwError } from 'rxjs';

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

  it('keeps catalog price and text boundaries aligned with the server', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    const form = fixture.componentInstance.productForm;
    const original = form.get('originalPrice')!;
    const marked = form.get('markedPrice')!;

    original.setValue(0);
    expect(original.invalid).toBeTrue();
    original.setValue(0.01);
    expect(original.valid).toBeTrue();
    original.setValue(99999999.99);
    expect(original.valid).toBeTrue();
    original.setValue(100000000);
    expect(original.invalid).toBeTrue();
    original.setValue(10.001);
    expect(original.invalid).toBeTrue();

    marked.setValue(null);
    expect(marked.valid).toBeTrue();
    marked.setValue(0);
    expect(marked.invalid).toBeTrue();
    form.get('label')!.setValue('x'.repeat(256));
    expect(form.get('label')!.invalid).toBeTrue();
    form.get('description')!.setValue('x'.repeat(256));
    expect(form.get('description')!.invalid).toBeTrue();
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

    spyOn(productService, 'getProductsPage').and.returnValue(of({
      items: [{id: 'prod-1', images: ['img-1']} as Product], page: 0, size: 20, total: 1, hasNext: false
    }));
    component.pages.load(0);
    expect(component.imageUrls['prod-1']).toBeTruthy();

    fixture.destroy();

    expect(revokeSpy).toHaveBeenCalled();
  });
  it('enforces the shared 0.01 to 100 kg range in controls and rendered input', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    const component: ProductManagementComponent = fixture.componentInstance;
    component.openModal();
    fixture.detectChanges();
    const control = component.productForm.get('weightKg')!;
    for (const value of [0.01, 0.011, 99.999, 100]) {
      control.setValue(value);
      expect(control.valid).withContext(String(value)).toBeTrue();
    }
    for (const value of [0.005, 0.009, 0.0101, 100.001, 1000]) {
      control.setValue(value);
      expect(control.invalid).withContext(String(value)).toBeTrue();
    }
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#productWeightKg');
    expect(input.min).toBe('0.01');
    expect(input.max).toBe('100');
    fixture.destroy();
  });
});

describe('ProductManagementComponent error UX (O2)', () => {
  let productOps: {
    getProductsPage: jasmine.Spy;
    getImage: jasmine.Spy;
    inverseProductVisibility: jasmine.Spy;
  };

  beforeEach(async () => {
    productOps = {
      getProductsPage: jasmine.createSpy('getProductsPage').and.returnValue(of({items: [], page: 0, size: 20, total: 0, hasNext: false})),
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
        { provide: CategoryService, useValue: { getCategoriesPage: (): Subject<never[]> => new Subject<never[]>() } },
        { provide: PackageService, useValue: { getPackagesPage: (): Subject<never[]> => new Subject<never[]>() } },
      ],
    }).compileComponents();
  });

  it('routes a failed product list load to an error alert (O2)', async () => {
    productOps.getProductsPage.and.returnValue(throwError(() => new Error('down')));

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
  let productService: {getProductsPage: jasmine.Spy; getImage: jasmine.Spy; updateProduct: jasmine.Spy};
  const product = (id: string, images: string[]): Product => ({id, images, label: 'Art', originalPrice: 10,
    markedPrice: 10, stockQuantity: 10, weightKg: 0.5, categoryId: 'cat', tags: [], availablePersonalizations: []});

  beforeEach(async () => {
    imageRequests = new Map<string, Subject<Blob>>();
    productService = {getProductsPage: jasmine.createSpy().and.returnValue(of({items: [], page: 0, size: 20, total: 0, hasNext: false})),
      getImage: jasmine.createSpy().and.callFake((path: string): Subject<Blob> => {
        const request: Subject<Blob> = new Subject<Blob>();
        imageRequests.set(path, request);
        return request;
      }), updateProduct: jasmine.createSpy().and.returnValue(new Subject<Product>())};
    await TestBed.configureTestingModule({imports: [ProductManagementComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      {provide: ProductService, useValue: productService},
      {provide: CategoryService, useValue: {getCategoriesPage: (): unknown => of({items: [], page: 0, size: 20, total: 0, hasNext: false})}},
      {provide: PackageService, useValue: {getPackagesPage: (): unknown => of({items: [], page: 0, size: 20, total: 0, hasNext: false})}},
    ]}).compileComponents();
  });

  function loadCoverPage(component: ProductManagementComponent, items: Product[], page: number = 0): void {
    productService.getProductsPage.and.returnValue(of({items, page, size: 20, total: 100, hasNext: true}));
    component.pages.load(page);
  }

  it('releases cover URLs and subscriptions on A to B to A page changes', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    fixture.detectChanges();
    const component: ProductManagementComponent = fixture.componentInstance;
    spyOn(URL, 'createObjectURL').and.returnValues('blob:A1', 'blob:B', 'blob:A2');
    const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    loadCoverPage(component, [product('A', ['a'])]);
    const oldA: Subject<Blob> = imageRequests.get('a')!;
    oldA.next(new Blob(['A']));
    loadCoverPage(component, [product('B', ['b'])], 1);
    expect(oldA.observed).toBeFalse();
    expect(component.imageUrls['A']).toBeUndefined();
    expect(revoke).toHaveBeenCalledWith('blob:A1');
    const oldB: Subject<Blob> = imageRequests.get('b')!;
    oldB.next(new Blob(['B']));
    loadCoverPage(component, [product('A', ['a'])]);
    expect(oldB.observed).toBeFalse();
    expect(component.imageUrls['B']).toBeUndefined();
    expect(revoke).toHaveBeenCalledWith('blob:B');
    imageRequests.get('a')!.next(new Blob(['A again']));
    expect(Object.keys(component.imageUrls)).toEqual(['A']);
    fixture.destroy();
    expect(revoke).toHaveBeenCalledWith('blob:A2');
  });

  it('cancels a pending cover when its page leaves and ignores later bytes', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    fixture.detectChanges();
    const component: ProductManagementComponent = fixture.componentInstance;
    const create: jasmine.Spy = spyOn(URL, 'createObjectURL').and.returnValue('blob:B');
    loadCoverPage(component, [product('A', ['a'])]);
    const pending: Subject<Blob> = imageRequests.get('a')!;
    loadCoverPage(component, [product('B', ['b'])], 1);
    expect(pending.observed).toBeFalse();
    pending.next(new Blob(['late A']));
    expect(create).not.toHaveBeenCalled();
    expect(component.imageUrls['A']).toBeUndefined();
    fixture.destroy();
  });

  it('retains repeated-page covers but releases a retained product refreshed without images', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    fixture.detectChanges();
    const component: ProductManagementComponent = fixture.componentInstance;
    spyOn(URL, 'createObjectURL').and.returnValue('blob:A');
    const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    loadCoverPage(component, [product('A', ['a'])]);
    const request: Subject<Blob> = imageRequests.get('a')!;
    request.next(new Blob(['A']));
    loadCoverPage(component, [product('A', ['a'])]);
    expect(productService.getImage).toHaveBeenCalledTimes(1);
    expect(revoke).not.toHaveBeenCalled();
    loadCoverPage(component, [product('A', [])]);
    expect(request.observed).toBeFalse();
    expect(revoke).toHaveBeenCalledWith('blob:A');
    expect(component.imageUrls['A']).toBeUndefined();
    fixture.destroy();
  });

  it('removes completed and failed cover subscriptions and stale error display entries', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    fixture.detectChanges();
    const component: ProductManagementComponent = fixture.componentInstance;
    const owner = component as unknown as {coverSubscriptions: Map<string, Subscription>};
    loadCoverPage(component, [product('A', ['a'])]);
    imageRequests.get('a')!.complete();
    expect(owner.coverSubscriptions.size).toBe(0);
    loadCoverPage(component, [product('B', ['b'])], 1);
    imageRequests.get('b')!.error(new Error('fixture image failure'));
    expect(owner.coverSubscriptions.size).toBe(0);
    loadCoverPage(component, [], 2);
    expect(Object.keys(component.imageUrls)).toEqual([]);
    fixture.destroy();
  });

  it('bounds live cover URLs and subscriptions to the current page across browsing', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    fixture.detectChanges();
    const component: ProductManagementComponent = fixture.componentInstance;
    const owner = component as unknown as {coverSubscriptions: Map<string, Subscription>};
    let created: number = 0;
    spyOn(URL, 'createObjectURL').and.callFake((): string => `blob:cover-${++created}`);
    const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    for (let page: number = 0; page < 100; page++) {
      loadCoverPage(component, [product(`p${page}`, [`image${page}`])], page);
      imageRequests.get(`image${page}`)!.next(new Blob(['cover']));
      expect(owner.coverSubscriptions.size).toBe(1);
      expect(Object.keys(component.imageUrls).length).toBe(1);
      expect(created - revoke.calls.count()).toBe(1);
    }
    fixture.destroy();
    expect(revoke.calls.count()).toBe(created);
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
