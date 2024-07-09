import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../../../service/product.service';
import { CategoryService } from '../../../service/category.service';
import { Category } from '../../../models/category.model';
import { Product } from '../../../models/product.model';
import {ProductRequest} from "../../../models/ProductRequest";

@Component({
  selector: 'app-admin-product-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-product-management.component.html',
  styleUrls: ['./admin-product-management.component.css']
})
export class ProductManagementComponent implements OnInit {
  private productService = inject(ProductService);
  private categoryService = inject(CategoryService);

  products = signal<Product[]>([]);
  categories = signal<Category[]>([]);
  newProduct = signal<Product>(this.getEmptyProduct());
  imageFiles = signal<File[]>([]);
  imagePreviews = signal<string[]>([]);
  isEditingProduct = signal(false);
  editProductData = signal<Product | null>(null);

  ngOnInit(): void {
    this.getProducts();
  }

  private getProducts(): void {
    this.productService.getProducts().subscribe({
      next: (response) => this.products.set(response),
      error: (error) => console.error('Error getting products:', error)
    });
  }

  private getCategories(): void {
    this.categoryService.getCategories().subscribe({
      next: (response) => this.categories.set(response),
      error: (error) => console.error('Error getting categories:', error)
    });
  }

  addProduct(): void {
    const product = this.newProduct();
    if (this.isValidProduct(product)) {
      const formData = new FormData();

      // Append product data as a JSON string
      formData.append('productDto', new Blob([JSON.stringify(product)], { type: 'application/json' }));

      // Append each file
      this.imageFiles().forEach((file, index) => {
        formData.append(`images`, file, file.name);
      });

      const productRequest: ProductRequest = {
        file: this.imageFiles(),
        dto: [product]
      };

      this.productService.addProduct(formData).subscribe({
        next: (response) => {
          this.products.update(prods => [...prods, response]);
          this.resetProductForm();
        },
        error: (error) => console.error('Error adding product:', error)
      });
    }
  }

  editProduct(product: Product): void {
    this.isEditingProduct.set(true);
    this.editProductData.set({...product});
    this.openModal();
  }

  updateProduct(): void {
    const product = this.editProductData();
    if (product && this.isValidProduct(product)) {
      this.productService.updateProduct(product.id!, product).subscribe({
        next: (response) => {
          this.products.update(prods =>
            prods.map(prod => prod.id === response.id ? response : prod)
          );
          this.resetProductForm();
        },
        error: (error) => console.error('Error updating product:', error)
      });
    }
  }

  deleteProduct(id: string | undefined): void {
    if (id) {
      this.productService.deleteProduct(id).subscribe({
        next: () => {
          this.products.update(prods => prods.filter(prod => prod.id !== id));
        },
        error: (error) => console.error('Error deleting product:', error)
      });
    }
  }

  openModal(): void {
    const modal = document.getElementById('productModal');
    modal?.classList.remove('hidden');
    if (!this.isEditingProduct()) {
      this.newProduct.set(this.getEmptyProduct());
    }
  }

  closeModal(): void {
    const modal = document.getElementById('productModal');
    modal?.classList.add('hidden');
    this.resetProductForm();
  }

  private getEmptyProduct(): Product {
    return {
      label: '',
      description: '',
      originalPrice: 0,
      markedPrice: 0,
      stockQuantity: 0,
      categoryId: '',
      //tags: new Set<string>(),
      images: []
    };
  }

  private isValidProduct(product: Product): boolean {
    return !!(product.label && product.description && product.categoryId);
  }

  private resetProductForm(): void {
    this.isEditingProduct.set(false);
    this.editProductData.set(null);
    this.newProduct.set(this.getEmptyProduct());
    this.imageFiles.set([]);
    this.imagePreviews.set([]);
    this.closeModal();
  }

  submitForm(): void {
    this.isEditingProduct() ? this.updateProduct() : this.addProduct();
  }

  onFileSelected(event: Event): void {
    const element = event.target as HTMLInputElement;
    const fileList: FileList | null = element.files;

    if (fileList) {
      const files = Array.from(fileList);
      this.imageFiles.set(files);
      this.processImageFiles(fileList);

      // Generate previews
      this.imagePreviews.set([]);
      Array.from(fileList).forEach(file => this.readAndAddImagePreview(file));
    }
  }

  private readAndAddImagePreview(file: File): void {
    const reader = new FileReader();
    reader.onload = (e: ProgressEvent<FileReader>) => {
      const result = e.target?.result;
      if (typeof result === 'string') {
        this.imagePreviews.update(previews => [...previews, result]);
      }
    };
    reader.readAsDataURL(file);
  }

  private processImageFiles(fileList: FileList): void {
    Array.from(fileList).forEach(file => {
      if (this.isValidImageFile(file)) {
        this.readAndAddImage(file);
      } else {
        console.error('File is not an image or exceeds 5MB limit:', file.name);
      }
    });
  }

  private isValidImageFile(file: File): boolean {
    return file.type.match(/image\/*/) !== null && file.size <= 5000000;
  }

  private readAndAddImage(file: File): void {
    const reader = new FileReader();
    reader.onload = (e: ProgressEvent<FileReader>) => {
      const result = e.target?.result;
      if (typeof result === 'string') {
        this.newProduct.update(prod => ({
          ...prod,
          images: [...prod.images, result]
        }));
      }
    };
    reader.readAsDataURL(file);
  }

  removeImage(index: number): void {
    console.log(index)
    this.newProduct.update(prod => ({
      ...prod,
      images: prod.images.filter((_, i) => i !== index)
    }));
    this.imageFiles.update(files => files.filter((_, i) => i !== index));
    this.imagePreviews.update(files => files.filter((_, i) => i !== index));
  }
}
