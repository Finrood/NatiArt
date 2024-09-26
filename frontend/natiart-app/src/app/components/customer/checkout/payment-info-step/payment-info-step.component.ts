import {Component, EventEmitter, Input, Output} from '@angular/core';
import {FormGroup, ReactiveFormsModule} from "@angular/forms";
import {NgClass, NgForOf, NgIf} from "@angular/common";

@Component({
  selector: 'app-payment-info-step',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    NgClass,
    NgIf,
    NgForOf
  ],
  templateUrl: './payment-info-step.component.html',
  styleUrl: './payment-info-step.component.css'
})
export class PaymentInfoStepComponent {
  @Input() checkoutForm!: FormGroup;
  @Output() processPixPayment = new EventEmitter<void>();

  getFieldError(fieldName: string): string {
    const field = this.checkoutForm.get(fieldName);
    if (field?.invalid && (field.dirty || field.touched)) {
      if (field.errors?.['required']) return 'This field is required.';
      if (field.errors?.['pattern']) return 'Please enter a valid card number.';
    }
    return '';
  }

  onProcessPixPayment() {
    this.processPixPayment.emit();
  }

  paymentMethods = [
    { value: 'credit_card', label: 'Credit Card' },
    { value: 'debit_card', label: 'Debit Card' },
    { value: 'pix', label: 'PIX' }
  ];

  isCardPayment(): boolean {
    const paymentMethod = this.checkoutForm.get('paymentInfo.paymentMethod')?.value;
    return paymentMethod === 'credit_card' || paymentMethod === 'debit_card';
  }

}
