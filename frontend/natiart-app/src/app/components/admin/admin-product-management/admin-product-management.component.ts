import {Component, HostListener, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {ProductService} from '../../../service/product.service';
import {CategoryService} from '../../../service/category.service';
import {Category} from '../../../models/category.model';
import {Product} from '../../../models/product.model';
import {BehaviorSubject, Subscription} from 'rxjs';
import {Alert} from '../../../models/alert.model';
import {DomSanitizer, SafeUrl} from "@angular/platform-browser";
import {CdkDragDrop, DragDropModule, moveItemInArray} from '@angular/cdk/drag-drop';
import heic2any from 'heic2any';
import {PackageService} from "../../../service/package.service";

interface ImagePreview {
  url: string | SafeUrl;
  isExisting: boolean;
  file?: File;
  originalUrl?: string;
}

@Component({
    selector: 'app-admin-product-management',
    imports: [CommonModule, ReactiveFormsModule, DragDropModule],
    templateUrl: './admin-product-management.component.html',
    styleUrls: ['./admin-product-management.component.css']
})
export class ProductManagementComponent implements OnInit {
  alert$ = new BehaviorSubject<Alert | null>(null);
  products = new BehaviorSubject<Product[]>([]);
  categories = new BehaviorSubject<Category[]>([]);
  packages = new BehaviorSubject<Category[]>([]);
  isEditingProduct = new BehaviorSubject(false);
  productForm: FormGroup;
  imagePreviews: ImagePreview[] = [];
  imageFiles: File[] = [];
  imageUrls: { [productId: string]: SafeUrl | null } = {};
  isTouch: boolean = false;
  isDragging: boolean = false;
  isLoadingImages: boolean = false;
  isSubmitting: boolean = false;
  protected sanitizer = inject(DomSanitizer);
  private productService = inject(ProductService);
  private categoryService = inject(CategoryService);
  private packageService = inject(PackageService);
  private fb = inject(FormBuilder);
  private subscriptions: Subscription[] = [];

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
      hasGold: [''],
      canPersonaliseGold: [false],
      canPersonaliseImage: [false],
      tags: [new Set<string>()],
      images: [[]],
      active: [true]
    });
  }

  @HostListener('window:resize', ['$event'])
  onResize(event: Event) {
    this.isTouch = 'ontouchstart' in window || navigator.maxTouchPoints > 0;
  }

  ngOnInit(): void {
    this.getProducts();
    this.getCategories();
    this.getPackages()
    this.isTouch = 'ontouchstart' in window || navigator.maxTouchPoints > 0;
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
  }

  closeModal(): void {
    const modal = document.getElementById('productModal');
    modal?.classList.add('hidden');
    this.productForm.reset();
    this.imageFiles = [];
    this.imagePreviews = [];
  }

  submitForm(): void {
    if (this.productForm.valid && !this.isSubmitting) {
      this.isSubmitting = true;
      const formData = new FormData();
      const product: Product = this.productForm.value;

      product.images = this.imagePreviews
        .filter(preview => preview.isExisting)
        .map(preview => (preview as any).originalUrl || preview.url as string);

      formData.append('productDto', new Blob([JSON.stringify(product)], {type: 'application/json'}));

      this.imagePreviews.filter(preview => !preview.isExisting).forEach(preview => {
        if (preview.file) {
          const fileToUpload = preview.file.type === 'image/jpeg' && preview.file.name.endsWith('.jpg')
            ? preview.file
            : preview.file;
          formData.append('newImages', fileToUpload, fileToUpload.name);
        }
      });

      if (this.isEditingProduct.value) {
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
        this.products.next(this.products.value.filter(prod => prod.id !== id));
        this.showAlert('Product deleted successfully', 'success');
      },
      error: (error) => {
        console.error('Error deleting product:', error);
        this.showAlert('Error deleting product', 'error');
      }
    });
  }

  openModal(product?: Product): void {
    if (product) {
      this.isEditingProduct.next(true);
      this.productForm.patchValue(product);
      this.imagePreviews = product.images?.map(imagePath => ({
        url: imagePath,
        isExisting: true
      })) || [];
      this.loadExistingImages(product.images || []);
    } else {
      this.isEditingProduct.next(false);
      this.productForm.reset({originalPrice: 0, markedPrice: 0, stockQuantity: 0});
      this.imagePreviews = [];
    }
    this.imageFiles = [];
    const modal = document.getElementById('productModal');
    modal?.classList.remove('hidden');
  }

  async onFileSelected(event: Event): Promise<void> {
    this.isLoadingImages = true;
    const element = event.target as HTMLInputElement;
    const fileList: FileList | null = element.files;

    if (fileList) {
      const newFiles = Array.from(fileList);
      this.imageFiles = [...this.imageFiles, ...newFiles];
      for (const file of newFiles) {
        await this.readAndAddImagePreview(file);
      }
    }
    this.isLoadingImages = false;
  }

  removeImage(index: number): void {
    if (!this.isDragging && index >= 0 && index < this.imagePreviews.length) {
      const removedPreview = this.imagePreviews.splice(index, 1)[0];
      if (!removedPreview.isExisting && removedPreview.file) {
        const fileIndex = this.imageFiles.indexOf(removedPreview.file);
        if (fileIndex > -1) {
          this.imageFiles.splice(fileIndex, 1);
        }
      }
      this.updateProductImages();
    }
  }

  onImageDrop(event: CdkDragDrop<ImagePreview[]>) {
    moveItemInArray(this.imagePreviews, event.previousIndex, event.currentIndex);
    this.updateProductImages();
  }

  dragStarted() {
    this.isDragging = true;
  }

  dragEnded() {
    setTimeout(() => {
      this.isDragging = false;
    }, 0);
  }

  toggleProductVisibility(product: Product): void {
    this.productService.inverseProductVisibility(product.id!).subscribe({
      next: (response: Product) => {
        this.products.next(this.products.value.map(prod => prod.id === response.id ? response : prod));
      },
      error: (error) => console.error('Error toggling product visibility:', error)
    });
  }

  addProduct(formData: FormData): void {
    this.productService.addProduct(formData).subscribe({
      next: (response) => {
        const updatedProducts = [...this.products.value, response];
        this.products.next(updatedProducts);
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

  updateProduct(productId: string, formData: FormData): void {
    this.productService.updateProduct(productId, formData).subscribe({
      next: (response: Product) => {
        const updatedProducts = this.products.value.map(prod => prod.id === response.id ? response : prod);
        this.products.next(updatedProducts);
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
        control.markAsTouched({onlySelf: true});
      } else if (control instanceof FormGroup) {
        this.validateAllFormFields(control);
      }
    });
  }

  private isValidImageFile(file: File): boolean {
    const isImageMimeType = file.type.match(/image\/*/) !== null || file.type.toLowerCase() === 'image/heic';
    const hasHeicExtension = /\.heic$/i.test(file.name.trim().toLowerCase());
    return isImageMimeType || hasHeicExtension;
  }

  private showAlert(message: string, type: 'success' | 'error'): void {
    this.alert$.next({message, type});
    setTimeout(() => this.alert$.next(null), 5000);
  }

  private getProducts(): void {
    this.productService.getProducts().subscribe({
      next: (response) => {
        this.products.next(response);
        this.updateAllProductImages(response);
      },
      error: (error) => console.error('Error getting products:', error)
    });
  }

  private updateProductImage(product: Product): void {
    if (product.images && product.images.length > 0) {
      this.fetchImage(product.id!, product.images[0]);
    }
  }

  private updateProductImages() {
    const existingImages = this.imagePreviews
      .filter(preview => preview.isExisting)
      .map(preview => (preview as any).originalUrl || preview.url as string);

    const newImages = this.imagePreviews
      .filter(preview => !preview.isExisting)
      .map(preview => preview.file)
      .filter((file): file is File => file !== undefined);

    this.productForm.patchValue({
      images: existingImages
    });

    this.imageFiles = newImages;
  }

  private loadExistingImages(imagePaths: string[]): void {
    imagePaths.forEach((path, index) => {
      this.fetchImagePreview(path, index);
    });
  }

  private fetchImagePreview(imagePath: string, index: number): void {
    this.productService.getImage(imagePath).subscribe(blob => {
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
  }

  private async readAndAddImagePreview(file: File): Promise<void> {
    if (this.isValidImageFile(file)) {
      let previewFile = file;
      let previewUrl: string | ArrayBuffer | null = null;

      if (file.name.toLowerCase().endsWith('.heic')) {
        try {
          const jpegBlob = await heic2any({
            blob: file,
            toType: 'image/jpeg',
            quality: 0.8
          }) as Blob;

          const singleJpegBlob = Array.isArray(jpegBlob) ? jpegBlob[0] : jpegBlob;

          previewFile = new File([singleJpegBlob], file.name.replace(/\.heic$/i, '.jpg'), {type: 'image/jpeg'});
          previewUrl = URL.createObjectURL(singleJpegBlob);
        } catch (error) {
          console.error('Error converting HEIC to JPEG:', error);
          return;
        }
      }

      if (!previewUrl) {
        const reader = new FileReader();
        reader.onload = (e: ProgressEvent<FileReader>) => {
          const result = e.target?.result;
          if (result) {
            this.addImagePreview(result, previewFile);
          } else {
            console.error('Failed to read file:', file.name);
          }
        };
        reader.readAsDataURL(previewFile);
      } else {
        this.addImagePreview(previewUrl, previewFile);
      }
    } else {
      console.error('File is not a valid image:', file.name);
    }
  }

  private addImagePreview(url: string | ArrayBuffer, file: File): void {
    this.imagePreviews.push({
      url: this.sanitizer.bypassSecurityTrustResourceUrl(url as string),
      isExisting: false,
      file: file
    });
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
      this.products.next([...this.products.value]);
    });
    this.subscriptions.push(subscription);
  }
}
