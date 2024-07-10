import {Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {ProductService} from '../../../service/product.service';
import {CategoryService} from '../../../service/category.service';
import {Category} from '../../../models/category.model';
import {Product} from '../../../models/product.model';
import {BehaviorSubject, Subscription} from 'rxjs';
import {Alert} from '../../../models/alert.model';
import {DomSanitizer, SafeUrl} from "@angular/platform-browser";

interface ImagePreview {
  url: string;
  isExisting: boolean;
  file?: File;
}

@Component({
  selector: 'app-admin-product-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './admin-product-management.component.html',
  styleUrls: ['./admin-product-management.component.css']
})
export class ProductManagementComponent implements OnInit {
  private productService = inject(ProductService);
  private categoryService = inject(CategoryService);
  private fb = inject(FormBuilder);
  protected sanitizer = inject(DomSanitizer);

  alert$ = new BehaviorSubject<Alert | null>(null);
  products = new BehaviorSubject<Product[]>([]);
  categories = new BehaviorSubject<Category[]>([]);
  isEditingProduct = new BehaviorSubject(false);
  productForm: FormGroup;
  imagePreviews: ImagePreview[] = [];
  imageFiles: File[] = [];

  imageUrls: { [productId: string]: SafeUrl | null } = {};
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
      tags: [new Set<string>()],
      images: [[]],
      active: [true]
    });
  }

  ngOnInit(): void {
    this.getProducts();
    this.getCategories();
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
  }

  private getCategories(): void {
    this.categoryService.getCategories().subscribe({
      next: (response) => this.categories.next(response),
      error: (error) => console.error('Error getting categories:', error)
    });
  }

  closeModal(): void {
    const modal = document.getElementById('productModal');
    modal?.classList.add('hidden');
    this.productForm.reset();
    this.imageFiles = [];
    this.imagePreviews = [];
  }

  submitForm(): void {
    if (this.productForm.valid) {
      const formData = new FormData();
      const product: Product = this.productForm.value;

      // Add existing image paths to the product object
      product.images = this.imagePreviews
        .filter(preview => preview.isExisting)
        .map(preview => preview.url);

      formData.append('productDto', new Blob([JSON.stringify(product)], { type: 'application/json' }));

      // Add only new images to formData
      this.imagePreviews.filter(preview => !preview.isExisting).forEach(preview => {
        if (preview.file) {
          formData.append('newImages', preview.file, preview.file.name);
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

  private isValidImageFile(file: File): boolean {
    return file.type.match(/image\/*/) !== null && file.size <= 5000000;
  }

  private showAlert(message: string, type: 'success' | 'error'): void {
    this.alert$.next({ message, type });
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

  openModal(product?: Product): void {
    if (product) {
      this.isEditingProduct.next(true);
      this.productForm.patchValue(product);
      this.imagePreviews = product.images?.map(imagePath => ({
        url: imagePath,
        isExisting: true
      })) || [];
    } else {
      this.isEditingProduct.next(false);
      this.productForm.reset({ originalPrice: 0, markedPrice: 0, stockQuantity: 0 });
      this.imagePreviews = [];
    }
    this.imageFiles = [];
    const modal = document.getElementById('productModal');
    modal?.classList.remove('hidden');
  }

  private fetchImagePreview(imagePath: string, index: number): void {
    this.productService.getImage(imagePath).subscribe(blob => {
      const reader = new FileReader();
      reader.onloadend = () => {
        this.imagePreviews[index].url = reader.result as string;
      };
      reader.readAsDataURL(blob);
    });
  }

  onFileSelected(event: Event): void {
    const element = event.target as HTMLInputElement;
    const fileList: FileList | null = element.files;

    if (fileList) {
      const newFiles = Array.from(fileList);
      this.imageFiles = [...this.imageFiles, ...newFiles];
      newFiles.forEach(file => this.readAndAddImagePreview(file));
    }
  }

  private readAndAddImagePreview(file: File): void {
    if (this.isValidImageFile(file)) {
      const reader = new FileReader();
      reader.onload = (e: ProgressEvent<FileReader>) => {
        const result = e.target?.result;
        if (typeof result === 'string') {
          this.imagePreviews.push({
            url: result,
            isExisting: false,
            file: file
          });
        }
      };
      reader.readAsDataURL(file);
    } else {
      console.error('File is not an image or exceeds 5MB limit:', file.name);
    }
  }

  removeImage(index: number): void {
    this.imagePreviews.splice(index, 1);
    if (!this.imagePreviews[index].isExisting) {
      const fileIndex = this.imageFiles.indexOf(this.imagePreviews[index].file!);
      if (fileIndex > -1) {
        this.imageFiles.splice(fileIndex, 1);
      }
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
      this.products.next([...this.products.value]); // Trigger change detection
    });
    this.subscriptions.push(subscription);
  }






  toggleProductVisibility(product: Product): void {
    this.productService.inverseProductVisibility(product.id!).subscribe({
      next: (response: Product) => {
        this.products.next(this.products.value.map(prod => prod.id === response.id ? response : prod));
      },
      error: (error) => console.error('Error toggling product visibility:', error)
    });
  }

  // Update the addProduct method
  addProduct(formData: FormData): void {
    this.productService.addProduct(formData).subscribe({
      next: (response) => {
        const updatedProducts = [...this.products.value, response];
        this.products.next(updatedProducts);
        this.updateProductImage(response);
        this.closeModal();
        this.showAlert('Product added successfully', 'success');
      },
      error: (error) => {
        console.error('Error adding product:', error);
        this.showAlert('Error adding product', 'error');
      }
    });
  }

  // Update the updateProduct method
  updateProduct(productId: string, formData: FormData): void {
    this.productService.updateProduct(productId, formData).subscribe({
      next: (response: Product) => {
        const updatedProducts = this.products.value.map(prod => prod.id === response.id ? response : prod);
        this.products.next(updatedProducts);
        this.updateProductImage(response);
        this.closeModal();
        this.showAlert('Product updated successfully', 'success');
      },
      error: (error) => {
        console.error('Error updating product:', error);
        this.showAlert('Error updating product', 'error');
      }
    });
  }
}
