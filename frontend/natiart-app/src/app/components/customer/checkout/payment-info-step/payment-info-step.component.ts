import {Component, Input} from '@angular/core';
import {FormGroup, ReactiveFormsModule} from "@angular/forms";
import {NgClass, NgIf} from "@angular/common";

@Component({
  selector: 'app-payment-info-step',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    NgClass,
    NgIf
  ],
  templateUrl: './payment-info-step.component.html',
  styleUrl: './payment-info-step.component.css'
})
export class PaymentInfoStepComponent {
  @Input() checkoutForm!: FormGroup;

  getFieldError(fieldName: string): string {
    const field = this.checkoutForm.get(fieldName);
    if (field?.invalid && (field.dirty || field.touched)) {
      if (field.errors?.['required']) return 'This field is required.';
      if (field.errors?.['pattern']) return 'Please enter a valid card number.';
    }
    return '';
  }

  onProcessPixPayment() {
    // Emit an event to the parent component to handle PIX payment
    // You might want to use @Output() and EventEmitter for this
  }
}
