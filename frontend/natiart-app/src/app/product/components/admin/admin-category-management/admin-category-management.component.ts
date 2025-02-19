import {Component, inject, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {CategoryService} from '../../../service/category.service';
import {Category} from '../../../models/category.model';
import {BehaviorSubject} from 'rxjs';
import {NatiartFormFieldComponent} from "../../../../shared/components/natiart-form-field/natiart-form-field.component";
import {AlertMessageComponent} from "../../../../shared/components/alert-message/alert-message.component";

@Component({
  selector: 'app-admin-category-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, NatiartFormFieldComponent, AlertMessageComponent],
  templateUrl: './admin-category-management.component.html',
  styleUrls: ['./admin-category-management.component.css']
})
export class CategoryManagementComponent implements OnInit {
  private _categories$ = new BehaviorSubject<Category[]>([]);
  categories$ = this._categories$.asObservable();

  isEditingCategory: boolean = false;
  modalVisible: boolean = false;

  categoryForm: FormGroup;
  private categoryService = inject(CategoryService);
  private fb = inject(FormBuilder);

  @ViewChild('alertMessages') alertMessageComponent!: AlertMessageComponent;

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

  openModal(category?: Category): void {
    this.isEditingCategory = !!category;
    if (category) {
      this.categoryForm.setValue({
        id: category.id || '',
        label: category.label,
        description: category.description || '',
        active: category.active ?? true
      });
    } else {
      this.categoryForm.reset({ active: true });
    }
    this.modalVisible = true;
  }

  closeModal(): void {
    this.modalVisible = false;
    this.categoryForm.reset();
  }

  submitForm(): void {
    if (this.categoryForm.valid) {
      this.isEditingCategory ? this.updateCategory() : this.addCategory();
    } else {
      this.validateAllFormFields(this.categoryForm);
    }
  }

  addCategory(): void {
    const category: Category = this.categoryForm.value;
    this.categoryService.addCategory(category).subscribe({
      next: (response) => {
        this._categories$.next([...this._categories$.value, response]);
        this.closeModal();
      },
      error: (error) => console.error('Error adding category:', error)
    });
  }

  updateCategory(): void {
    const category: Category = this.categoryForm.value;
    this.categoryService.updateCategory(category.id!, category).subscribe({
      next: (response: Category) => {
        this._categories$.next(
          this._categories$.value.map(cat => cat.id === response.id ? response : cat)
        );
        this.closeModal();
      },
      error: (error) => console.error('Error updating category:', error)
    });
  }

  deleteCategory(id: string): void {
    this.categoryService.deleteCategory(id).subscribe({
      next: () => {
        this._categories$.next(this._categories$.value.filter(cat => cat.id !== id));
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
        this._categories$.next(
          this._categories$.value.map(cat => cat.id === response.id ? response : cat)
        );
      },
      error: (error) => console.error('Error toggling category visibility:', error)
    });
  }

  private getCategories(): void {
    this.categoryService.getCategories().subscribe({
      next: (response) => this._categories$.next(response),
      error: (error) => console.error('Error getting categories:', error)
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
    this.alertMessageComponent.showAlert({ message, type });
  }
}
