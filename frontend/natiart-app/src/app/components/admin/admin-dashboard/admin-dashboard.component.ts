import { Component, OnInit } from '@angular/core';
import { FormsModule } from "@angular/forms";
import {NgClass, NgForOf, NgIf} from "@angular/common";
import { Product } from "../../../models/product.model";
import { Category } from "../../../models/category.model";
import { HttpClient } from "@angular/common/http";
import { Router } from "@angular/router";
import { TokenService } from "../../../service/token.service";
import { ProductService } from "../../../service/product.service";
import { CategoryService } from "../../../service/category.service";

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [
    FormsModule,
    NgIf,
    NgForOf,
    NgClass
  ],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnInit {
  products: Product[] = [];
  categories: Category[] = [];
  activeTab: 'categories' | 'products' = 'categories';
  newCategory: Partial<Category> = { label: '', description: '' };
  newProduct: Partial<Product> = {
    label: '',
    description: '',
    originalPrice: 0,
    markedPrice: 0,
    stockQuantity: 0,
    category: {} as Category,
    tags: new Set<string>(),
    images: []
  };

  isEditingCategory: boolean = false;
  isEditingProduct: boolean = false;
  editCategoryData: Category = { id: '', label: '', description: ''};
  editProductData: Product | null = null;

  constructor(
    private productService: ProductService,
    private categoryService: CategoryService
  ) {}

  ngOnInit(): void {
    this.getProducts();
    this.getCategories();
  }

  getProducts(): void {
    this.productService.getProducts()
      .subscribe({
        next: (response) => {
          this.products = response;
        },
        error: (error) => {
          console.log('Error getting products: ', error);
        }
      });
  }

  getCategories(): void {
    this.categoryService.getCategories()
      .subscribe({
        next: (response) => {
          this.categories = response;
        },
        error: (error) => {
          console.log('Error getting categories: ', error);
        }
      });
  }

  addCategory(): void {
    if (this.newCategory.label && this.newCategory.label.trim()) {
      this.categoryService.addCategory(this.newCategory)
        .subscribe({
          next: (response) => {
            this.categories.push(response);
            this.newCategory = { label: '', description: '' };
            this.closeCategoryModal();
          },
          error: (error) => {
            console.log('Error adding category: ', error);
          }
        });
    }
  }

  addProduct(): void {
    if (this.newProduct.label && this.newProduct.description && this.newProduct.category) {
      this.productService.addProduct(this.newProduct)
        .subscribe({
          next: (response) => {
            this.products.push(response);
            this.newProduct = {
              label: '',
              description: '',
              originalPrice: 0,
              markedPrice: 0,
              stockQuantity: 0,
              category: {} as Category,
              tags: new Set<string>(),
              images: []
            };
            this.closeProductModal();
          },
          error: (error) => {
            console.log('Error adding product: ', error);
          }
        });
    }
  }

  editCategory(category: Category): void {
    this.isEditingCategory = true;
    this.editCategoryData = { ...category };
    this.openCategoryModal();
  }

  updateCategory(): void {
    if (this.editCategoryData && this.editCategoryData.label.trim()) {
      this.categoryService.updateCategory(this.editCategoryData.id!, this.editCategoryData)
        .subscribe({
          next: (response) => {
            const index = this.categories.findIndex(cat => cat.id === response.id);
            this.categories[index] = response;
            this.isEditingCategory = false;
            this.editCategoryData = { id: '', label: '', description: '' };
            this.closeCategoryModal();
          },
          error: (error) => {
            console.log('Error updating category: ', error);
          }
        });
    }
  }

  toggleCategoryVisibility(category: Category): void {
    this.categoryService.inverseCategoryVisibility(category.id!)
      .subscribe({
        next: (response: Category) => {
          const index = this.categories.findIndex(cat => cat.id === response.id);
          this.categories[index] = response;
        },
        error: (error: string) => {
          console.log('Error toggling category visibility: ', error);
        }
      });
  }

  openCategoryModal(): void {
    const modal = document.getElementById('categoryModal');
    if (modal) modal.classList.remove('hidden');
  }

  closeCategoryModal(): void {
    const modal = document.getElementById('categoryModal');
    if (modal) modal.classList.add('hidden');
    this.isEditingCategory = false;
    this.editCategoryData = { id: '', label: '', description: '' };
  }

  submitCategoryForm(): void {
    if (this.isEditingCategory) {
      this.updateCategory();
    } else {
      this.addCategory();
    }
  }

  editProduct(product: Product): void {
    this.isEditingProduct = true;
    this.editProductData = { ...product };
    this.openProductModal();
  }

  updateProduct(): void {
    if (this.editProductData && this.editProductData.label && this.editProductData.description && this.editProductData.category) {
      this.productService.updateProduct(this.editProductData.id!, this.editProductData)
        .subscribe({
          next: (response) => {
            const index = this.products.findIndex(prod => prod.id === response.id);
            this.products[index] = response;
            this.isEditingProduct = false;
            this.editProductData = null;
            this.closeProductModal();
          },
          error: (error) => {
            console.log('Error updating product: ', error);
          }
        });
    }
  }

  deleteCategory(id: string | undefined): void {
    if (id) {
      this.categoryService.inverseCategoryVisibility(id)
        .subscribe({
          next: () => {
            //TODO
          },
          error: (error) => {
            console.log('Error deleting category: ', error);
          }
        });
    }
  }

  deleteProduct(id: string | undefined): void {
    if (id) {
      this.productService.deleteProduct(id)
        .subscribe({
          next: () => {
            //TODO
          },
          error: (error) => {
            console.log('Error deleting product: ', error);
          }
        });
    }
  }

  openProductModal(): void {
    const modal = document.getElementById('productModal');
    if (modal) modal.classList.remove('hidden');
  }

  closeProductModal(): void {
    const modal = document.getElementById('productModal');
    if (modal) modal.classList.add('hidden');
    this.isEditingProduct = false;
    this.editProductData = null;
    this.newProduct = {
      label: '',
      description: '',
      originalPrice: 0,
      markedPrice: 0,
      stockQuantity: 0,
      category: {} as Category,
      tags: new Set<string>(),
      images: []
    };
  }

  submitProductForm(): void {
    if (this.isEditingProduct) {
      this.updateProduct();
    } else {
      this.addProduct();
    }
  }
}
