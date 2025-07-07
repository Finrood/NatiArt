import {Component, EventEmitter, Input, Output} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Product} from '../../../models/product.model';
import {PersonalizationOption} from "../../../models/support/personalization-option";
import {ButtonComponent} from "../../../../shared/components/button.component"; // Ensure this path is correct

@Component({
  selector: 'app-personalization-modal',
  standalone: true, // Add standalone: true
  imports: [CommonModule, FormsModule, ButtonComponent], // CommonModule and FormsModule are needed for *ngIf, [(ngModel)] etc.
  templateUrl: './personalization-modal.component.html',
  styleUrl: './personalization-modal.component.css'
})
export class PersonalizationModalComponent {
  @Input() show = false;
  @Input() product: Product | null = null;
  @Output() close = new EventEmitter<void>();
  @Output() personalize = new EventEmitter<{ goldBorder?: boolean, customImage?: File }>();

  // Internal state for the form elements
  goldBorder = false;
  customImage: File | null = null;

  // Helper getters to check available personalizations
  get canAddGoldBorder(): boolean {
    return !!this.product?.availablePersonalizations.includes(PersonalizationOption.GOLDEN_BORDER);
  }

  get canAddCustomImage(): boolean {
    return !!this.product?.availablePersonalizations.includes(PersonalizationOption.CUSTOM_IMAGE);
  }

  onFileSelected(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) {
      this.customImage = file;
    } else {
      // Handle case where user cancels file selection
      this.customImage = null;
    }
  }

  onCancel() {
    this.resetForm();
    this.close.emit();
  }

  onSubmit() {
    if (!this.isValid()) {
      // Optional: Add some user feedback if they somehow click submit when invalid
      console.warn("Submit clicked while form is invalid.");
      return;
    }
    this.personalize.emit({
      // Only include options if they are available for the product
      goldBorder: this.canAddGoldBorder ? this.goldBorder : undefined,
      customImage: this.canAddCustomImage ? this.customImage ?? undefined : undefined // Use nullish coalescing for clarity
    });
    // Important: Close the modal after submitting
    this.close.emit();
    this.resetForm(); // Reset form for next time it opens
  }

  isValid(): boolean {
    // If custom image is an available personalization for this product,
    // then the customImage file MUST be selected.
    if (this.canAddCustomImage) {
      return !!this.customImage; // Must have a file selected
    }
    // Otherwise (only gold border available, or no options), it's always valid.
    return true;
  }

  private resetForm() {
    this.goldBorder = false;
    this.customImage = null;
    // Reset file input visually if needed (more complex, often not necessary if modal is destroyed/recreated)
    // const fileInput = document.getElementById('customImage') as HTMLInputElement;
    // if (fileInput) {
    //   fileInput.value = '';
    // }
  }
}
