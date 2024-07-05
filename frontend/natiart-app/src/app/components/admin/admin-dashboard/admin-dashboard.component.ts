import { Component, OnInit, inject, signal, computed, effect } from '@angular/core';
import { CommonModule, NgOptimizedImage } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../../../service/product.service';
import {CategoryService} from "../../../service/category.service";
import { Product } from '../../../models/product.model';
import { Category } from '../../../models/category.model';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, NgOptimizedImage],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnInit {
  private productService = inject(ProductService);
  private categoryService = inject(CategoryService);

  products = signal<Product[]>([]);
  categories = signal<Category[]>([]);
  activeTab = signal<'categories' | 'products'>('categories');
  newCategory = signal<Category>({label: '', description: ''});
  newProduct = signal<Product>({
    label: '',
    description: '',
    originalPrice: 0,
    markedPrice: 0,
    stockQuantity: 0,
    category: {} as Category,
    tags: new Set<string>(),
    images: []
  });

  isEditingCategory = signal(false);
  isEditingProduct = signal(false);
  editCategoryData = signal<Category>({id: '', label: '', description: ''});
  editProductData = signal<Product | null>(null);

  visibleCategories = computed(() => this.categories().filter(cat => cat.active));
  visibleProducts = computed(() => this.products().filter(prod => prod.category.active));

  constructor() {
    effect(() => {
      console.log('Active tab changed:', this.activeTab());
      this.loadData();
    });
  }

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    if (this.activeTab() === 'categories') {
      this.getCategories();
    } else {
      this.getProducts();
    }
  }

  getProducts(): void {
    this.productService.getProducts().subscribe({
      next: (response) => this.products.set(response),
      error: (error) => console.error('Error getting products:', error)
    });
  }

  getCategories(): void {
    this.categoryService.getCategories().subscribe({
      next: (response) => this.categories.set(response),
      error: (error) => console.error('Error getting categories:', error)
    });
  }

  addProduct(): void {
    const product = this.newProduct();
    if (product.label && product.description && product.category) {
      this.productService.addProduct(product).subscribe({
        next: (response) => {
          this.products.update(prods => [...prods, response]);
          this.resetProductForm();
        },
        error: (error) => console.error('Error adding product:', error)
      });
    }
  }

  toggleCategoryVisibility(category: Category): void {
    this.categoryService.inverseCategoryVisibility(category.id!).subscribe({
      next: (response: Category) => {
        this.categories.update(cats =>
          cats.map(cat => cat.id === response.id ? response : cat)
        );
      },
      error: (error) => console.error('Error toggling category visibility:', error)
    });
  }

  editProduct(product: Product): void {
    this.isEditingProduct.set(true);
    this.editProductData.set({...product});
    this.openModal('product');
  }

  updateProduct(): void {
    const product = this.editProductData();
    if (product && product.label && product.description && product.category) {
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

  deleteCategory(id: string | undefined): void {
    if (id) {
      this.categoryService.inverseCategoryVisibility(id).subscribe({
        next: () => {
          this.categories.update(cats => cats.filter(cat => cat.id !== id));
        },
        error: (error) => console.error('Error deleting category:', error)
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

  onFileSelected(event: Event): void {
    const element = event.target as HTMLInputElement;
    const fileList: FileList | null = element.files;

    if (fileList) {
      Array.from(fileList).forEach(file => {
        if (file.type.match(/image\/*/) && file.size <= 5000000) {
          const reader = new FileReader();
          reader.onload = (e: ProgressEvent<FileReader>) => {
            const result = e.target?.result;
            if (typeof result === 'string') {
              if (this.isEditingProduct()) {
                this.editProductData.update(prod => ({
                  ...prod!,
                  images: [...(prod!.images || []), result]
                }));
              } else {
                this.newProduct.update(prod => ({
                  ...prod,
                  images: [...(prod.images || []), result]
                }));
              }
            }
          };
          reader.readAsDataURL(file);
        } else {
          console.error('File is not an image or exceeds 5MB limit:', file.name);
        }
      });
    }
  }

  removeImage(index: number): void {
    if (this.isEditingProduct()) {
      this.editProductData.update(prod => ({
        ...prod!,
        images: prod!.images?.filter((_, i) => i !== index)
      }));
    } else {
      this.newProduct.update(prod => ({
        ...prod,
        images: prod.images?.filter((_, i) => i !== index)
      }));
    }
  }

  private resetProductForm(): void {
    this.isEditingProduct.set(false);
    this.editProductData.set(null);
    this.newProduct.set({
      label: '',
      description: '',
      originalPrice: 0,
      markedPrice: 0,
      stockQuantity: 0,
      category: {} as Category,
      tags: new Set<string>(),
      images: []
    });
    this.closeModal('product');
  }

  openModal(type: 'category' | 'product'): void {
    const modal = document.getElementById(`${type}Modal`);
    modal?.classList.remove('hidden');
    if (type === 'category' && !this.isEditingCategory()) {
      this.newCategory.set({id: '', label: '', description: ''});
    }
  }

  closeModal(type: 'category' | 'product'): void {
    const modal = document.getElementById(`${type}Modal`);
    modal?.classList.add('hidden');
    if (type === 'category') {
      this.isEditingCategory.set(false);
    } else {
      this.isEditingProduct.set(false);
    }
    this.resetForm(type);
  }

  submitForm(type: 'category' | 'product'): void {
    if (type === 'category') {
      this.isEditingCategory() ? this.updateCategory() : this.addCategory();
    } else {
      this.isEditingProduct() ? this.updateProduct() : this.addProduct();
    }
  }

  addCategory(): void {
    const category = this.newCategory();
    if (category.label?.trim()) {
      this.categoryService.addCategory(category).subscribe({
        next: (response) => {
          this.categories.update(cats => [...cats, response]);
          this.resetCategoryForm();
        },
        error: (error) => console.error('Error adding category:', error)
      });
    }
  }

  editCategory(category: Category): void {
    this.isEditingCategory.set(true);
    this.editCategoryData.set({...category});
    this.newCategory.set({...category});
    this.openModal('category');
  }

  updateCategory(): void {
    const category = this.newCategory();
    if (category.id && category.label?.trim()) {
      this.categoryService.updateCategory(category.id, category).subscribe({
        next: (response: Category) => {
          this.categories.update(cats =>
            cats.map(cat => cat.id === response.id ? response : cat)
          );
          this.resetCategoryForm();
        },
        error: (error) => console.error('Error updating category:', error)
      });
    }
  }

  private resetCategoryForm(): void {
    this.isEditingCategory.set(false);
    this.editCategoryData.set({id: '', label: '', description: ''});
    this.newCategory.set({id: '', label: '', description: ''});
    this.closeModal('category');
  }

  private resetForm(type: 'category' | 'product'): void {
    type === 'category' ? this.resetCategoryForm() : this.resetProductForm();
  }
}
