import {AfterViewInit, Component, inject, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {HttpErrorResponse} from '@angular/common/http';
import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {CategoryService} from '../../../service/category.service';
import {Category} from '../../../models/category.model';
import {BehaviorSubject} from 'rxjs';
import {finalize} from 'rxjs/operators';
import {NatiartFormFieldComponent} from "../../../../shared/components/natiart-form-field/natiart-form-field.component";
import {AlertMessageComponent} from "../../../../shared/components/alert-message/alert-message.component";
import {ButtonComponent} from "../../../../shared/components/button.component";
import {reportError} from '../../../../shared/service/error-reporting.service';

@Component({
  selector: 'app-admin-category-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, NatiartFormFieldComponent, AlertMessageComponent, ButtonComponent],
  templateUrl: './admin-category-management.component.html',
  styleUrls: ['./admin-category-management.component.css']
})
export class CategoryManagementComponent implements OnInit, AfterViewInit {
  private _categories$ = new BehaviorSubject<Category[]>([]);
  categories$ = this._categories$.asObservable();

  isEditingCategory: boolean = false;
  modalVisible: boolean = false;
  isSubmitting = false;

  categoryForm: FormGroup;
  private categoryService = inject(CategoryService);
  private fb = inject(FormBuilder);
  private formGeneration = 0;
  private pendingAlerts: Array<{message: string; type: 'success' | 'error'}> = [];

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

  ngAfterViewInit(): void {
    this.pendingAlerts.splice(0).forEach(alert => this.showAlert(alert.message, alert.type));
  }

  openModal(category?: Category): void {
    this.formGeneration++;
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
    this.formGeneration++;
    this.modalVisible = false;
    this.categoryForm.reset();
  }

  submitForm(): void {
    if (this.isSubmitting) {
      return;
    }
    if (this.categoryForm.valid) {
      this.isSubmitting = true;
      const generation = this.formGeneration;
      this.isEditingCategory ? this.updateCategory(generation) : this.addCategory(generation);
    } else {
      this.validateAllFormFields(this.categoryForm);
    }
  }

  addCategory(generation = this.formGeneration): void {
    const category: Category = this.categoryForm.value;
    this.categoryService.addCategory(category).pipe(finalize(() => this.isSubmitting = false)).subscribe({
      next: (response) => {
        this._categories$.next([...this._categories$.value, response]);
        if (generation === this.formGeneration) {
          this.closeModal();
        }
        this.showAlert('Category created successfully', 'success');
      },
      error: (error) => {
        reportError('category', error);
        this.showAlert(this.writeErrorMessage(error), 'error');
      }
    });
  }

  updateCategory(generation = this.formGeneration): void {
    const category: Category = this.categoryForm.value;
    this.categoryService.updateCategory(category.id!, category).pipe(finalize(() => this.isSubmitting = false)).subscribe({
      next: (response: Category) => {
        this._categories$.next(
          this._categories$.value.map(cat => cat.id === response.id ? response : cat)
        );
        if (generation === this.formGeneration) {
          this.closeModal();
        }
        this.showAlert('Category updated successfully', 'success');
      },
      error: (error) => {
        reportError('category', error);
        this.showAlert(this.writeErrorMessage(error), 'error');
      }
    });
  }

  deleteCategory(id: string): void {
    const category = this._categories$.value.find(item => item.id === id);
    if (!category || !window.confirm(`Delete category "${category.label}"?`)) {
      return;
    }
    this.categoryService.deleteCategory(id).subscribe({
      next: () => {
        this._categories$.next(this._categories$.value.filter(cat => cat.id !== id));
        this.showAlert('Category deleted successfully', 'success');
      },
      error: (error: HttpErrorResponse) => {
        reportError('category', error);
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
      error: (error) => {
        reportError('category', error);
        this.showAlert('Unable to change category visibility. Please retry.', 'error');
      }
    });
  }

  private getCategories(): void {
    this.categoryService.getCategories().subscribe({
      next: (response) => this._categories$.next(response),
      error: (error) => {
        reportError('category', error);
        this.showAlert('Unable to load categories. Please retry.', 'error');
      }
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
    if (this.alertMessageComponent) {
      this.alertMessageComponent.showAlert({ message, type });
    } else {
      this.pendingAlerts.push({message, type});
    }
  }

  private writeErrorMessage(error: HttpErrorResponse): string {
    if (error.status === 409) {
      return 'A category with this label already exists.';
    }
    if (error.status === 0) {
      return 'The category service is unavailable. Please retry.';
    }
    return 'Unable to save the category. Your changes are still in the form.';
  }
}
