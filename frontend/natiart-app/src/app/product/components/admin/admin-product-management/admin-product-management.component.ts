import {AccessibleDialogComponent} from '../../../../shared/components/accessible-dialog.component';
import {ChangeDetectorRef, DestroyRef, AfterViewInit, Component, HostListener, inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {ProductService} from '../../../service/product.service';
import {CategoryService} from '../../../service/category.service';
import {PackageService} from '../../../service/package.service';
import {Category} from '../../../models/category.model';
import {Package} from '../../../models/package.model';
import {Product} from '../../../models/product.model';
import {BehaviorSubject, finalize, Subscription} from 'rxjs';
import {DomSanitizer, SafeUrl} from '@angular/platform-browser';
import {CdkDragDrop, DragDropModule, moveItemInArray} from '@angular/cdk/drag-drop';
import {PersonalizationOption} from '../../../models/support/personalization-option';
import {ImageService} from '../../../service/image.service';
import {AlertMessageComponent} from "../../../../shared/components/alert-message/alert-message.component";
import {PagedList} from '../../../../shared/service/paged-list';
import {PageControlsComponent} from '../../../../shared/components/page-controls.component';
import {ButtonComponent} from "../../../../shared/components/button.component";
import {reportError, reportWarning} from '../../../../shared/service/error-reporting.service';

interface ImagePreview {
  id: string;
  objectUrl?: string;
  url: string | SafeUrl;
  isExisting: boolean;
  file?: File;
  originalUrl?: string;
}

@Component({
  selector: 'app-admin-product-management',
  imports: [CommonModule, ReactiveFormsModule, DragDropModule, AlertMessageComponent, ButtonComponent, PageControlsComponent, AccessibleDialogComponent],
  templateUrl: './admin-product-management.component.html',
  styleUrls: ['./admin-product-management.component.css']
})
export class ProductManagementComponent implements OnInit, AfterViewInit, OnDestroy {
  private _products$ = new BehaviorSubject<Product[]>([]);
  products$ = this._products$.asObservable();
  categories = new BehaviorSubject<Category[]>([]);
  packages = new BehaviorSubject<Package[]>([]);

  // Use plain booleans for modal and editing state
  isEditingProduct: boolean = false;
  modalVisible: boolean = false;

  productForm: FormGroup;
  imagePreviews: ImagePreview[] = [];
  imageFiles: File[] = [];
  imageUrls: { [productId: string]: SafeUrl | null } = {};
  isTouch: boolean = false;
  isDragging: boolean = false;
  isLoadingImages: boolean = false;
  isSubmitting: boolean = false;

  @ViewChild('alertMessages') alertMessageComponent!: AlertMessageComponent;

  private sanitizer = inject(DomSanitizer);
  private productService = inject(ProductService);
  private categoryService = inject(CategoryService);
  private packageService = inject(PackageService);
  private fb = inject(FormBuilder);
  private imageService = inject(ImageService);
  private subscriptions: Subscription[] = [];
  private objectUrlsCreated: string[] = [];
  private pendingAlerts: Array<{ message: string; type: 'success' | 'error' }> = [];
  private pendingAlertsTimer: ReturnType<typeof setTimeout> | undefined = undefined;
  private imageSessionGeneration = 0;
  private readonly _cdr = inject(ChangeDetectorRef);
  private readonly previewSubscriptions = new Map<string, Subscription>();
  private readonly coverSubscriptions = new Map<string, Subscription>();
  private readonly coverObjectUrls = new Map<string, string>();

  availablePersonalizationOptions = Object.values(PersonalizationOption);

  constructor() {
    this.productForm = this.fb.group({
      id: [''],
      label: ['', Validators.required],
      description: [''],
      originalPrice: [0, [Validators.required, Validators.min(0)]],
      markedPrice: [0, Validators.min(0)],
      stockQuantity: [0, [Validators.required, Validators.min(0)]],
      weightKg: [0, [Validators.required, Validators.min(0.001)]],
      categoryId: ['', Validators.required],
      packageId: [''],
      hasFixedGoldenBorder: [''],
      GOLDEN_BORDER: [{ value: false, disabled: false }],
      CUSTOM_IMAGE: [false],
      tags: [new Set<string>()],
      images: [[]],
      active: [true],
      newProduct: [false],
      featuredProduct: [false]
    });
  }

  @HostListener('window:resize')
  onResize(): void {
    this.isTouch = 'ontouchstart' in window || navigator.maxTouchPoints > 0;
  }

  readonly pages = new PagedList<Product>((page: number) => this.productService.getProductsPage(undefined, page, 20, '', true),
    (items: Product[]): void => {this._products$.next(items); this.updateAllProductImages(items);}, inject(DestroyRef), (): void => this.showAlert($localize`Error loading products`, 'error'));

  readonly categoryOptions = new PagedList<Category>((page: number) => this.categoryService.getCategoriesPage(page, 20, true),
    (items: Category[]): void => this.categories.next([...new Map([...this.categories.value, ...items]
      .map((item: Category) => [item.id, item] as const)).values()]), inject(DestroyRef));
  readonly packageOptions = new PagedList<Package>((page: number) => this.packageService.getPackagesPage(page, 20, true),
    (items: Package[]): void => this.packages.next([...new Map([...this.packages.value, ...items]
      .map((item: Package) => [item.id, item] as const)).values()]), inject(DestroyRef));

  ngOnInit(): void {
    this.getProducts();
    this.getCategories();
    this.getPackages();
    this.isTouch = 'ontouchstart' in window || navigator.maxTouchPoints > 0;

    const borderToggle = this.productForm.get('hasFixedGoldenBorder')?.valueChanges.subscribe((hasFixed) => {
      const goldenControl = this.productForm.get('GOLDEN_BORDER');
      if (hasFixed) {
        goldenControl?.setValue(false);
        goldenControl?.disable();
      } else {
        goldenControl?.enable();
      }
    });
    if (borderToggle) {
      this.subscriptions.push(borderToggle);
    }
  }

  ngOnDestroy(): void {
    this.releasePreviews();
    for (const id of this.coverPaths.keys()) this.releaseCover(id);
    this.imageSessionGeneration++;
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
    this.objectUrlsCreated.forEach((url: string) => URL.revokeObjectURL(url));
    this.objectUrlsCreated = [];
    if (this.pendingAlertsTimer !== undefined) {
      clearTimeout(this.pendingAlertsTimer);
      this.pendingAlertsTimer = undefined;
    }
  }

  ngAfterViewInit(): void {
    // Flush outside the current change-detection pass: the child view is
    // already checked, so pushing alerts synchronously here trips NG0100.
    this.pendingAlertsTimer = setTimeout(() => {
      this.pendingAlertsTimer = undefined;
      const pending = this.pendingAlerts;
      this.pendingAlerts = [];
      pending.forEach(alert => this.showAlert(alert.message, alert.type));
    }, 0);
  }

  openModal(product?: Product): void {
    this.releasePreviews();
    const sessionGeneration = ++this.imageSessionGeneration;
    this.isEditingProduct = !!product;
    if (product) {
      this.productForm.patchValue(product);

      if (product.availablePersonalizations.includes(PersonalizationOption.GOLDEN_BORDER)) {
        this.productForm.get('GOLDEN_BORDER')?.setValue(true);
      }

      if (product.availablePersonalizations.includes(PersonalizationOption.CUSTOM_IMAGE)) {
        this.productForm.get('CUSTOM_IMAGE')?.setValue(true);
      }

      this.imagePreviews = Array.from(new Set(product.images || [])).map(imagePath => ({
        id: imagePath,
        url: imagePath,
        isExisting: true,
        originalUrl: imagePath
      }));
      this.loadExistingImages(this.imagePreviews.map((preview: ImagePreview) => preview.originalUrl!), sessionGeneration);
    } else {
      this.productForm.reset({
        weightKg: 0,
        originalPrice: 0,
        markedPrice: 0,
        stockQuantity: 0,
        active: true,
        newProduct: false,
        featuredProduct: false,
      });
      this.imagePreviews = [];
    }
    this.imageFiles = [];
    this.modalVisible = true;
  }

  closeModal(): void {
    this.releasePreviews();
    this.imageSessionGeneration++;
    this.modalVisible = false;
    this.productForm.reset();
    this.imageFiles = [];
    this.imagePreviews = [];
    this.isLoadingImages = false;
  }

  submitForm(): void {
    if (this.productForm.valid && !this.isSubmitting && !this.isLoadingImages) {
      this.isSubmitting = true;
      const formData = new FormData();
      const product: Product & {imageManifest?: Array<{existingImage?: string; uploadId?: string}>} = this.productForm.getRawValue();

      product.availablePersonalizations = []
      if (this.productForm.get('GOLDEN_BORDER')?.getRawValue() === true) {
        product.availablePersonalizations.push(PersonalizationOption.GOLDEN_BORDER);
      }
      if (this.productForm.get('CUSTOM_IMAGE')?.getRawValue() === true) {
        product.availablePersonalizations.push(PersonalizationOption.CUSTOM_IMAGE);
      }

        // Get existing image URLs from previews.
      product.images = this.imagePreviews
        .filter(preview => preview.isExisting)
        .map(preview => preview.originalUrl || preview.url as string);

      product.imageManifest = this.imagePreviews.map((preview: ImagePreview) => preview.isExisting
        ? {existingImage: preview.originalUrl!} : {uploadId: preview.id});
      formData.append('productDto', new Blob([JSON.stringify(product)], { type: 'application/json' }));
      this.imagePreviews
        .filter(preview => !preview.isExisting)
        .forEach(preview => {
          if (preview.file) {
            formData.append('newImages', preview.file, `${preview.id}.webp`);
          }
        });

      if (this.isEditingProduct) {
        this.updateProduct(product.id!, formData);
      } else {
        this.addProduct(formData);
      }
    } else {
      this.validateAllFormFields(this.productForm);
    }
  }

  deleteProduct(id: string): void {
    this.productService.deleteProduct(id).subscribe({
      next: () => {
        this.pages.load(this.pages.$page());
        this._products$.next(this._products$.value.filter(prod => prod.id !== id));
        this.showAlert($localize`Product deleted successfully`, 'success');
      },
      error: (error) => {
        reportError('product-management', error);
        this.showAlert($localize`Error deleting product`, 'error');
      }
    });
  }

  toggleProductVisibility(product: Product): void {
    this.productService.inverseProductVisibility(product.id!).subscribe({
      next: (response: Product) => {
        this.pages.load(this.pages.$page());
        this._products$.next(this._products$.value.map(prod => prod.id === response.id ? response : prod));
      },
      error: (error) => {
        reportError('product-management', error);
        this.showAlert($localize`Error changing product visibility`, 'error');
      }
    });
  }

  private addProduct(formData: FormData): void {
    this.productService.addProduct(formData).subscribe({
      next: (response) => {
        this.pages.load(this.pages.$page());
        this._products$.next([...this._products$.value, response]);
        this.updateProductImage(response);
        this.closeModal();
        this.showAlert($localize`Product added successfully`, 'success');
        this.isSubmitting = false;
      },
      error: (error) => {
        reportError('product-management', error);
        this.showAlert($localize`Error adding product`, 'error');
        this.isSubmitting = false;
      }
    });
  }

  private updateProduct(productId: string, formData: FormData): void {
    this.productService.updateProduct(productId, formData).subscribe({
      next: (response: Product) => {
        this.pages.load(this.pages.$page());
        this._products$.next(this._products$.value.map(prod => prod.id === response.id ? response : prod));
        this.updateProductImage(response);
        this.closeModal();
        this.showAlert($localize`Product updated successfully`, 'success');
        this.isSubmitting = false;
      },
      error: (error) => {
        reportError('product-management', error);
        this.showAlert($localize`Error updating product`, 'error');
        this.isSubmitting = false;
      }
    });
  }

  private getProducts(): void { this.pages.load(0); }

  private getCategories(): void { this.categoryOptions.load(0); }

  private getPackages(): void { this.packageOptions.load(0); }

  private validateAllFormFields(formGroup: FormGroup): void {
    Object.keys(formGroup.controls).forEach(field => {
      const control = formGroup.get(field);
      if (control instanceof FormControl) {
        control.markAsTouched({ onlySelf: true });
      } else if (control instanceof FormGroup) {
        this.validateAllFormFields(control);
      }
    });
  }

  private showAlert(message: string, type: 'success' | 'error'): void {
    // List loads fire in ngOnInit, before the alert child resolves: queue
    // those so page-load failures still surface instead of throwing.
    if (this.alertMessageComponent) {
      this.alertMessageComponent.showAlert({ message, type });
    } else {
      this.pendingAlerts.push({ message, type });
    }
  }

  private updateProductImage(product: Product): void {
    if (product.images && product.images.length > 0) {
      this.fetchImage(product.id!, product.images[0]);
    } else if (product.id) {
      this.coverSubscriptions.get(product.id)?.unsubscribe();
      const previous: string | undefined = this.coverObjectUrls.get(product.id);
      if (previous) this.revokeObjectUrl(previous);
      this.coverObjectUrls.delete(product.id);
      this.imageUrls[product.id] = null;
    }
  }

  private readonly coverPaths: Map<string, string> = new Map<string, string>();

  private updateAllProductImages(products: Product[]): void {
    const desired: Map<string, string> = new Map<string, string>();
    for (const product of products) {
      if (product.id && product.images?.length) desired.set(product.id, product.images[0]);
    }
    for (const id of new Set<string>([...this.coverPaths.keys(), ...Object.keys(this.imageUrls)])) {
      if (!desired.has(id) || desired.get(id) !== this.coverPaths.get(id)) this.releaseCover(id);
    }
    for (const [id, path] of desired) {
      if (this.coverPaths.get(id) === path) continue;
      this.coverPaths.set(id, path);
      this.fetchImage(id, path);
    }
  }

  private releaseCover(productId: string): void {
    this.coverSubscriptions.get(productId)?.unsubscribe();
    this.coverSubscriptions.delete(productId);
    const previous: string | undefined = this.coverObjectUrls.get(productId);
    if (previous) this.revokeObjectUrl(previous);
    this.coverObjectUrls.delete(productId);
    this.coverPaths.delete(productId);
    delete this.imageUrls[productId];
  }

  private fetchImage(productId: string, imagePath: string): void {
    const subscription: Subscription = this.productService.getImage(imagePath).pipe(
      finalize((): void => {this.coverSubscriptions.delete(productId);})
    ).subscribe({
      next: (blob: Blob): void => {
        if (this.coverPaths.get(productId) !== imagePath) return;
        const previous: string | undefined = this.coverObjectUrls.get(productId);
        if (previous) this.revokeObjectUrl(previous);
        const objectUrl: string = URL.createObjectURL(blob);
        this.coverObjectUrls.set(productId, objectUrl);
        this.objectUrlsCreated.push(objectUrl);
        this.imageUrls[productId] = this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl);
        this._products$.next([...this._products$.value]);
      },
      error: (error: unknown): void => {
        reportError('product-image', error);
        this.releaseCover(productId);
        this.imageUrls[productId] = null;
      }
    });
    if (!subscription.closed) this.coverSubscriptions.set(productId, subscription);
  }

  private loadExistingImages(imagePaths: string[], sessionGeneration: number): void {
    imagePaths.forEach((path: string): void => this.fetchImagePreview(path, path, sessionGeneration));
  }

  private fetchImagePreview(imagePath: string, previewId: string, sessionGeneration: number): void {
    const subscription: Subscription = this.productService.getImage(imagePath).subscribe({
      next: (blob: Blob): void => {
        const index: number = this.imagePreviews.findIndex((preview: ImagePreview) => preview.id === previewId);
        if (sessionGeneration !== this.imageSessionGeneration || index < 0) return;
        const objectUrl: string = URL.createObjectURL(blob);
        this.objectUrlsCreated.push(objectUrl);
        const previous: string | undefined = this.imagePreviews[index].objectUrl;
        if (previous) this.revokeObjectUrl(previous);
        this.imagePreviews[index] = {...this.imagePreviews[index], objectUrl,
          url: this.sanitizer.bypassSecurityTrustUrl(objectUrl)};
        this._cdr.markForCheck();
      },
      error: (error: unknown): void => {
        if (sessionGeneration !== this.imageSessionGeneration
          || !this.imagePreviews.some((preview: ImagePreview) => preview.id === previewId)) return;
        reportError('product-image', error);
        this.showAlert($localize`Error loading product image`, 'error');
      }
    });
    this.previewSubscriptions.set(previewId, subscription);
  }

  private revokeObjectUrl(url: string): void {
    URL.revokeObjectURL(url);
    this.objectUrlsCreated = this.objectUrlsCreated.filter((entry: string) => entry !== url);
  }

  private releasePreviews(): void {
    this.previewSubscriptions.forEach((subscription: Subscription): void => subscription.unsubscribe());
    this.previewSubscriptions.clear();
    this.imagePreviews.forEach((preview: ImagePreview): void => {
      if (preview.objectUrl) this.revokeObjectUrl(preview.objectUrl);
    });
  }

  async onFileSelected(event: Event): Promise<void> {
    const sessionGeneration = this.imageSessionGeneration;
    this.isLoadingImages = true;
    const element = event.target as HTMLInputElement;
    const fileList: FileList | null = element.files;
    if (fileList) {
      const newFiles = Array.from(fileList);
      for (const file of newFiles) {
        if (sessionGeneration !== this.imageSessionGeneration) {
          return;
        }
        if (this.imageService.isValidImageFile(file)) {
          try {
            const converted: File = await this.imageService.convertFile(file);
            if (sessionGeneration !== this.imageSessionGeneration) {
              return;
            }
            const objectUrl: string = URL.createObjectURL(converted);
            this.objectUrlsCreated.push(objectUrl);
            this.imagePreviews.push({
              id: crypto.randomUUID(),
              objectUrl,
              url: this.sanitizer.bypassSecurityTrustUrl(objectUrl),
              isExisting: false,
              file: converted
            });
            this.imageFiles.push(converted);
          } catch (error) {
            reportError('image-conversion', error);
          }
        } else {
          reportWarning('invalid-input');
        }
      }
    }
    if (sessionGeneration === this.imageSessionGeneration) {
      this.isLoadingImages = false;
      this._cdr.markForCheck();
    }
  }

  onImageDrop(event: CdkDragDrop<ImagePreview[]>): void {
    moveItemInArray(this.imagePreviews, event.previousIndex, event.currentIndex);
    this.updateProductImages();
  }

  removeImage(previewId: string): void {
    const index: number = this.imagePreviews.findIndex((preview: ImagePreview) => preview.id === previewId);
    if (!this.isDragging && index >= 0 && index < this.imagePreviews.length) {
      this.previewSubscriptions.get(previewId)?.unsubscribe();
      this.previewSubscriptions.delete(previewId);
      const objectUrl: string | undefined = this.imagePreviews[index].objectUrl;
      if (objectUrl) this.revokeObjectUrl(objectUrl);
      this.imagePreviews.splice(index, 1);
      this.updateProductImages();
    }
  }

  dragStarted(): void {
    this.isDragging = true;
  }

  dragEnded(): void {
    setTimeout(() => {
      this.isDragging = false;
    }, 0);
  }

  private updateProductImages(): void {
    const existingImages = this.imagePreviews
      .filter(preview => preview.isExisting)
      .map(preview => preview.originalUrl || preview.url as string);
    this.productForm.patchValue({ images: existingImages });
    this.imageFiles = this.imagePreviews
      .filter(preview => !preview.isExisting && preview.file)
      .map(preview => preview.file!) || [];
  }

  getPersonalizationOptionLabel(option: string): string {
    switch (option) {
      case 'None': return $localize`No personalization available`;
      case 'CUSTOM_IMAGE': return $localize`Customer can personalize the image`;
      case 'GOLDEN_BORDER': return $localize`Customer can choose if borders are golden`;
      default: return option;
    }
  }

  protected readonly PersonalizationOption = PersonalizationOption;
}
