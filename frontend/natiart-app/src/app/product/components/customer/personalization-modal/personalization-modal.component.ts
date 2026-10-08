import {AccessibleDialogComponent} from '../../../../shared/components/accessible-dialog.component';
import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, signal} from '@angular/core';

import {FormsModule} from '@angular/forms';
import {Product} from '../../../models/product.model';
import {PersonalizationOption} from "../../../models/support/personalization-option";
import {ButtonComponent} from "../../../../shared/components/button.component";
import {reportWarning} from '../../../../shared/service/error-reporting.service';

@Component({
  selector: 'app-personalization-modal',
  imports: [FormsModule, ButtonComponent, AccessibleDialogComponent],
  templateUrl: './personalization-modal.component.html',
  styleUrl: './personalization-modal.component.css'
})
export class PersonalizationModalComponent implements OnChanges {
  @Input() show: boolean = false;
  @Input() closeOnSubmit: boolean = true;
  @Input() pending: boolean = false;
  @Input() errorMessage: string = '';
  readonly $fileError = signal('');

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['show'] && !this.show) this.resetForm();
  }
  @Input() product: Product | null = null;
  @Output() close = new EventEmitter<void>();
  @Output() personalize = new EventEmitter<{ goldBorder?: boolean, customImage?: File }>();

  // Internal state for the form elements
  goldBorder = false;
  customImage: File | null = null;

  // Helper getters to check available personalizations
  get canAddGoldBorder(): boolean {
    return !!this.product?.availablePersonalizations?.includes(PersonalizationOption.GOLDEN_BORDER);
  }

  get canAddCustomImage(): boolean {
    return !!this.product?.availablePersonalizations?.includes(PersonalizationOption.CUSTOM_IMAGE);
  }

  onFileSelected(event: Event): void {
    this.$fileError.set('');
    const file: File | undefined = (event.target as HTMLInputElement).files?.[0];
    if (file && (!file.type.startsWith('image/') || file.size === 0 || file.size > 5_000_000)) {
      this.customImage = null;
      this.$fileError.set($localize`Choose an image file up to 5 MB.`);
      (event.target as HTMLInputElement).value = '';
    } else if (file) {
      this.customImage = file;
    } else {
      // Handle case where user cancels file selection
      this.customImage = null;
    }
  }

  onCancel(): void {
    this.resetForm();
    this.close.emit();
  }

  onSubmit(): void {
    if (this.pending || !this.isValid()) {
      // Optional: Add some user feedback if they somehow click submit when invalid
      reportWarning('invalid-input');
      return;
    }
    this.personalize.emit({
      // Only include options if they are available for the product
      goldBorder: this.canAddGoldBorder ? this.goldBorder : undefined,
      customImage: this.canAddCustomImage ? this.customImage ?? undefined : undefined // Use nullish coalescing for clarity
    });
    if (this.closeOnSubmit) {
      this.close.emit();
      this.resetForm();
    }
  }

  isValid(): boolean {
    if (this.$fileError()) return false;
    // If custom image is an available personalization for this product,
    // then the customImage file MUST be selected.
    if (this.canAddCustomImage) {
      return !!this.customImage; // Must have a file selected
    }
    // Otherwise (only gold border available, or no options), it's always valid.
    return true;
  }

  private resetForm(): void {
    this.$fileError.set('');
    this.goldBorder = false;
    this.customImage = null;
    // Reset file input visually if needed (more complex, often not necessary if modal is destroyed/recreated)
    // const fileInput = document.getElementById('customImage') as HTMLInputElement;
    // if (fileInput) {
    //   fileInput.value = '';
    // }
  }
}
