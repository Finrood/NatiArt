import {Component, HostListener, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {ProductService} from '../../../service/product.service';
import {CategoryService} from '../../../service/category.service';
import {PackageService} from '../../../service/package.service';
import {Category} from '../../../models/category.model';
import {Product} from '../../../models/product.model';
import {BehaviorSubject, Subscription} from 'rxjs';
import {Alert} from '../../../models/alert.model';
import {DomSanitizer, SafeUrl} from '@angular/platform-browser';
import {CdkDragDrop, DragDropModule, moveItemInArray} from '@angular/cdk/drag-drop';
import {PersonalizationOption} from '../../../models/support/personalization-option';
import {ImageService} from '../../../service/image.service';

interface ImagePreview {
  url: string | SafeUrl;
  isExisting: boolean;
  file?: File;
  originalUrl?: string;
}

@Component({
  selector: 'app-admin-product-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, DragDropModule],
  templateUrl: './admin-product-management.component.html',
  styleUrls: ['./admin-product-management.component.css']
})
export class ProductManagementComponent implements OnInit, OnDestroy {
  alert$ = new BehaviorSubject<Alert | null>(null);
  private _products$ = new BehaviorSubject<Product[]>([]);
  products$ = this._products$.asObservable();
  categories = new BehaviorSubject<Category[]>([]);
  packages = new BehaviorSubject<Category[]>([]);

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

  private sanitizer = inject(DomSanitizer);
  private productService = inject(ProductService);
  private categoryService = inject(CategoryService);
  private packageService = inject(PackageService);
  private fb = inject(FormBuilder);
  private imageService = inject(ImageService);
  private subscriptions: Subscription[] = [];

  availablePersonalizationOptions = Object.values(PersonalizationOption);

  constructor() {
    this.productForm = this.fb.group({
      id: [''],
      label: ['', Validators.required],
      description: [''],
      originalPrice: [0, [Validators.required, Validators.min(0)]],
      markedPrice: [0, Validators.min(0)],
      stockQuantity: [0, [Validators.required, Validators.min(0)]],
      categoryId: ['', Validators.required],
      packageId: [''],
      hasFixedGoldenBorder: [''],
      GOLDEN_BORDER: [{ value: false, disabled: false }],
      CUSTOM_IMAGE: [false],
      tags: [new Set<string>()],
      images: [[]],
      active: [true]
    });
  }

  @HostListener('window:resize', ['$event'])
  onResize(): void {
    this.isTouch = 'ontouchstart' in window || navigator.maxTouchPoints > 0;
  }

  ngOnInit(): void {
    this.getProducts();
    this.getCategories();
    this.getPackages();
    this.isTouch = 'ontouchstart' in window || navigator.maxTouchPoints > 0;

    this.productForm.get('hasFixedGoldenBorder')?.valueChanges.subscribe((hasFixed) => {
      const goldenControl = this.productForm.get('GOLDEN_BORDER');
      if (hasFixed) {
        goldenControl?.setValue(false);
        goldenControl?.disable();
      } else {
        goldenControl?.enable();
      }
    });
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
  }

  openModal(product?: Product): void {
    this.isEditingProduct = !!product;
    if (product) {
      this.productForm.patchValue(product);

      if (product.availablePersonalizations.includes(PersonalizationOption.GOLDEN_BORDER)) {
        this.productForm.get('GOLDEN_BORDER')?.setValue(true);
      }

      if (product.availablePersonalizations.includes(PersonalizationOption.CUSTOM_IMAGE)) {
        this.productForm.get('CUSTOM_IMAGE')?.setValue(true);
      }

      this.imagePreviews = (product.images || []).map(imagePath => ({
        url: imagePath,
        isExisting: true,
        originalUrl: imagePath
      }));
      this.loadExistingImages(product.images || []);
    } else {
      this.productForm.reset({ originalPrice: 0, markedPrice: 0, stockQuantity: 0 });
      this.imagePreviews = [];
    }
    this.imageFiles = [];
    this.modalVisible = true;
  }

  closeModal(): void {
    this.modalVisible = false;
    this.productForm.reset();
    this.imageFiles = [];
    this.imagePreviews = [];
  }

  submitForm(): void {
    if (this.productForm.valid && !this.isSubmitting) {
      this.isSubmitting = true;
      const formData = new FormData();
      const product: Product = this.productForm.getRawValue();

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
        .map(preview => (preview as any).originalUrl || preview.url as string);

      formData.append('productDto', new Blob([JSON.stringify(product)], { type: 'application/json' }));
      this.imagePreviews
        .filter(preview => !preview.isExisting)
        .forEach(preview => {
          if (preview.file) {
            formData.append('newImages', preview.file, preview.file.name);
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
        this._products$.next(this._products$.value.filter(prod => prod.id !== id));
        this.showAlert('Product deleted successfully', 'success');
      },
      error: (error) => {
        console.error('Error deleting product:', error);
        this.showAlert('Error deleting product', 'error');
      }
    });
  }

  toggleProductVisibility(product: Product): void {
    this.productService.inverseProductVisibility(product.id!).subscribe({
      next: (response: Product) => {
        this._products$.next(this._products$.value.map(prod => prod.id === response.id ? response : prod));
      },
      error: (error) => console.error('Error toggling product visibility:', error)
    });
  }

  private addProduct(formData: FormData): void {
    this.productService.addProduct(formData).subscribe({
      next: (response) => {
        this._products$.next([...this._products$.value, response]);
        this.updateProductImage(response);
        this.closeModal();
        this.showAlert('Product added successfully', 'success');
        this.isSubmitting = false;
      },
      error: (error) => {
        console.error('Error adding product:', error);
        this.showAlert('Error adding product', 'error');
        this.isSubmitting = false;
      }
    });
  }

  private updateProduct(productId: string, formData: FormData): void {
    this.productService.updateProduct(productId, formData).subscribe({
      next: (response: Product) => {
        this._products$.next(this._products$.value.map(prod => prod.id === response.id ? response : prod));
        this.updateProductImage(response);
        this.closeModal();
        this.showAlert('Product updated successfully', 'success');
        this.isSubmitting = false;
      },
      error: (error) => {
        console.error('Error updating product:', error);
        this.showAlert('Error updating product', 'error');
        this.isSubmitting = false;
      }
    });
  }

  private getProducts(): void {
    this.productService.getProducts().subscribe({
      next: (response: Product[]) => {
        console.log(response)
        this._products$.next(response);
        this.updateAllProductImages(response);
      },
      error: (error) => console.error('Error getting products:', error)
    });
  }

  private getCategories(): void {
    this.categoryService.getCategories().subscribe({
      next: (response) => this.categories.next(response),
      error: (error) => console.error('Error getting categories:', error)
    });
  }

  private getPackages(): void {
    this.packageService.getPackages().subscribe({
      next: (response) => this.packages.next(response),
      error: (error) => console.error('Error getting packages:', error)
    });
  }

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
    this.alert$.next({ message, type });
    setTimeout(() => this.alert$.next(null), 5000);
  }

  private updateProductImage(product: Product): void {
    if (product.images && product.images.length > 0) {
      this.fetchImage(product.id!, product.images[0]);
    }
  }

  private updateAllProductImages(products: Product[]): void {
    products.forEach(product => {
      if (product.images && product.images.length > 0) {
        this.fetchImage(product.id!, product.images[0]);
      }
    });
  }

  private fetchImage(productId: string, imagePath: string): void {
    const subscription = this.productService.getImage(imagePath).subscribe(blob => {
      const objectUrl = URL.createObjectURL(blob);
      this.imageUrls[productId] = this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl);
      this._products$.next([...this._products$.value]);
    });
    this.subscriptions.push(subscription);
  }

  private loadExistingImages(imagePaths: string[]): void {
    imagePaths.forEach((path, index) => {
      this.fetchImagePreview(path, index);
    });
  }

  private fetchImagePreview(imagePath: string, index: number): void {
    const subscription = this.productService.getImage(imagePath).subscribe(blob => {
      const reader = new FileReader();
      reader.onloadend = () => {
        this.imagePreviews[index] = {
          url: this.sanitizer.bypassSecurityTrustResourceUrl(reader.result as string),
          isExisting: true,
          originalUrl: imagePath
        };
      };
      reader.readAsDataURL(blob);
    });
    this.subscriptions.push(subscription);
  }

  async onFileSelected(event: Event): Promise<void> {
    this.isLoadingImages = true;
    const element = event.target as HTMLInputElement;
    const fileList: FileList | null = element.files;
    if (fileList) {
      const newFiles = Array.from(fileList);
      for (const file of newFiles) {
        if (this.imageService.isValidImageFile(file)) {
          try {
            const preview = await this.imageService.generateImagePreview(file);
            this.imagePreviews.push({
              url: preview.url,
              isExisting: false,
              file: preview.file
            });
            this.imageFiles.push(preview.file);
          } catch (error) {
            console.error('Error processing image:', error);
          }
        } else {
          console.error('Invalid image file:', file.name);
        }
      }
    }
    this.isLoadingImages = false;
  }

  onImageDrop(event: CdkDragDrop<ImagePreview[]>): void {
    moveItemInArray(this.imagePreviews, event.previousIndex, event.currentIndex);
    this.updateProductImages();
  }

  removeImage(index: number): void {
    if (!this.isDragging && index >= 0 && index < this.imagePreviews.length) {
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
      .map(preview => (preview as any).originalUrl || preview.url as string);
    this.productForm.patchValue({ images: existingImages });
    this.imageFiles = this.imagePreviews
      .filter(preview => !preview.isExisting && preview.file)
      .map(preview => preview.file!) || [];
  }

  getPersonalizationOptionLabel(option: string): string {
    switch (option) {
      case 'None': return 'No personalization available';
      case 'CUSTOM_IMAGE': return 'Customer can personalize the image';
      case 'GOLDEN_BORDER': return 'Customer can choose if borders are golden';
      default: return option;
    }
  }

  protected readonly PersonalizationOption = PersonalizationOption;
}
