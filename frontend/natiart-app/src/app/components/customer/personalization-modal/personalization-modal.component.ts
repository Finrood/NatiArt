import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Product } from '../../../models/product.model';

@Component({
  selector: 'app-personalization-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div *ngIf="show" class="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full flex items-center justify-center">
      <div class="bg-white p-5 rounded-lg shadow-xl max-w-md w-full">
        <h2 class="text-xl font-bold mb-4">Personalize Your Product</h2>
        <p class="mb-4">{{ product?.label }}</p>
        <div *ngIf="product?.canPersonaliseGold" class="mb-4">
          <label class="inline-flex items-center">
            <input type="checkbox" class="form-checkbox" [(ngModel)]="goldBorder">
            <span class="ml-2">Add Gold Border</span>
          </label>
        </div>
        <div *ngIf="product?.canPersonaliseImage" class="mb-4">
          <label class="block mb-2">Custom Image</label>
          <input type="file" (change)="onFileSelected($event)" accept="image/*"
                 class="mt-1 block w-full px-3 py-2 bg-white border border-primary rounded-md shadow-input placeholder-secondary-light focus:outline-none focus:ring-primary focus:border-primary sm:text-sm">
          <p *ngIf="customImage" class="mt-2 text-sm text-gray-600">
            Selected file: {{ customImage.name }}
          </p>
        </div>
        <div class="flex justify-end space-x-2">
          <button (click)="onCancel()" class="flex-1 py-2 px-4  rounded-md shadow-input text-sm font-medium bg-transparent text-primary border border-primary focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary transition-colors duration-300 disabled:opacity-50 disabled:cursor-not-allowed">Cancel</button>
          <button (click)="onSubmit()" [disabled]="!isValid()"
                  class="flex-1 py-2 px-4 border border-transparent rounded-md shadow-input text-sm font-medium text-white bg-primary hover:bg-primary-dark focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary transition-colors duration-300 disabled:opacity-50 disabled:cursor-not-allowed">
            Add to Cart
          </button>
        </div>
      </div>
    </div>
  `
})
export class PersonalizationModalComponent {
  @Input() show = false;
  @Input() product: Product | null = null;
  @Output() close = new EventEmitter<void>();
  @Output() personalize = new EventEmitter<{ goldBorder?: boolean, customImage?: File }>();

  goldBorder = false;
  customImage: File | null = null;

  onFileSelected(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) {
      this.customImage = file;
    }
  }

  onCancel() {
    this.resetForm();
    this.close.emit();
  }

  onSubmit() {
    this.personalize.emit({
      goldBorder: this.goldBorder,
      customImage: this.customImage || undefined
    });
    this.resetForm();
  }

  isValid() {
    if (this.product?.canPersonaliseImage) {
      return this.customImage;
    } else {
      return true;
    }
  }

  private resetForm() {
    this.goldBorder = false;
    this.customImage = null;
  }
}
