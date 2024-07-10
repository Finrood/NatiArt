import {Component, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {CategoryService} from '../../../service/category.service';
import {Category} from '../../../models/category.model';
import {BehaviorSubject} from 'rxjs';
import {Alert} from '../../../models/alert.model';

@Component({
  selector: 'app-admin-category-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './admin-category-management.component.html',
  styleUrls: ['./admin-category-management.component.css']
})
export class CategoryManagementComponent implements OnInit {
  private categoryService = inject(CategoryService);
  private fb = inject(FormBuilder);

  alert$ = new BehaviorSubject<Alert | null>(null);
  categories = new BehaviorSubject<Category[]>([]);
  isEditingCategory = new BehaviorSubject(false);
  categoryForm: FormGroup;

  constructor() {
    this.categoryForm = this.fb.group({
      id: [''],
      label: ['', Validators.required],
      description: [''],
      active: [true]
    });
  }

  ngOnInit(): void {
    this.getCategories();
  }

  private getCategories(): void {
    this.categoryService.getCategories().subscribe({
      next: (response) => this.categories.next(response),
      error: (error) => console.error('Error getting categories:', error)
    });
  }

  openModal(category?: Category): void {
    if (category) {
      this.isEditingCategory.next(true);
      this.categoryForm.setValue({
        id: category.id || '',
        label: category.label,
        description: category.description || '',
        active: category.active ?? true
      });
    } else {
      this.isEditingCategory.next(false);
      this.categoryForm.reset({ active: true });
    }
    const modal = document.getElementById('categoryModal');
    modal?.classList.remove('hidden');
  }

  closeModal(): void {
    const modal = document.getElementById('categoryModal');
    modal?.classList.add('hidden');
    this.categoryForm.reset();
  }

  submitForm(): void {
    if (this.categoryForm.valid) {
      if (this.isEditingCategory.value) {
        this.updateCategory();
      } else {
        this.addCategory();
      }
    } else {
      this.validateAllFormFields(this.categoryForm);
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

  addCategory(): void {
    if (this.categoryForm.valid) {
      const category: Category = this.categoryForm.value;
      this.categoryService.addCategory(category).subscribe({
        next: (response) => {
          this.categories.next([...this.categories.value, response]);
          this.closeModal();
        },
        error: (error) => console.error('Error adding category:', error)
      });
    }
  }

  updateCategory(): void {
    if (this.categoryForm.valid) {
      const category: Category = this.categoryForm.value;
      this.categoryService.updateCategory(category.id!, category).subscribe({
        next: (response: Category) => {
          this.categories.next(this.categories.value.map(cat => cat.id === response.id ? response : cat));
          this.closeModal();
        },
        error: (error) => console.error('Error updating category:', error)
      });
    }
  }

  deleteCategory(id: string): void {
    this.categoryService.deleteCategory(id).subscribe({
      next: () => {
        this.categories.next(this.categories.value.filter(cat => cat.id !== id));
        this.showAlert('Category deleted successfully', 'success');
      },
      error: (error: any) => {
        console.error('Error deleting category:', error);
        let errorMessage = 'An error occurred while deleting the category.';
        if (error.status === 400) {
          errorMessage = 'Category contains existing products. Delete them before deleting this category';
        } else if (error.status === 404) {
          errorMessage = 'Category not found. It may have been already deleted.';
        } else if (error.status === 403) {
          errorMessage = 'You do not have permission to delete this category.';
        }
        this.showAlert(errorMessage, 'error');
      }
    });
  }

  toggleCategoryVisibility(category: Category): void {
    this.categoryService.inverseCategoryVisibility(category.id!).subscribe({
      next: (response: Category) => {
        this.categories.next(this.categories.value.map(cat => cat.id === response.id ? response : cat));
      },
      error: (error) => console.error('Error toggling category visibility:', error)
    });
  }

  private showAlert(message: string, type: 'success' | 'error'): void {
    this.alert$.next({ message, type });
    setTimeout(() => this.alert$.next(null), 5000);
  }
}
