import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {FormGroup, ReactiveFormsModule} from "@angular/forms";
import {CommonModule, NgForOf, NgIf} from "@angular/common";
import {animate, style, transition, trigger} from "@angular/animations";
import {
  NatiartFormFieldComponent
} from "../../../../../shared/components/natiart-form-field/natiart-form-field.component";
import {PaymentMethod} from "../../../../models/paymentMethod.model";

@Component({
  selector: 'app-payment-info-step',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    NgIf,
    NgForOf,
    NatiartFormFieldComponent,
    NatiartFormFieldComponent
  ],
  templateUrl: './payment-info-step.component.html',
  animations: [
    trigger('fadeIn', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(5px)' }),
        animate('200ms ease-out', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
      transition(':leave', [
        animate('200ms ease-in', style({ opacity: 0, transform: 'translateY(5px)' }))
      ])
    ]),
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PaymentInfoStepComponent {
  @Input({ required: true }) checkoutForm!: FormGroup;

  paymentMethods = [
    {value: PaymentMethod.CREDIT_CARD, label: 'Credit Card'},
    {value: PaymentMethod.DEBIT_CARD, label: 'Debit Card'},
    {value: PaymentMethod.PIX, label: 'PIX'}
  ];

  get paymentInfoGroup(): FormGroup { // Helper getter
    return this.checkoutForm.get('paymentInfo') as FormGroup;
  }

  isCardPaymentSelected(): boolean {
    const paymentMethod = this.checkoutForm.get('paymentInfo.paymentMethod')?.value;
    return paymentMethod === PaymentMethod.CREDIT_CARD || paymentMethod === PaymentMethod.DEBIT_CARD;
  }

  isPixSelected(): boolean {
    return this.checkoutForm.get('paymentInfo.paymentMethod')?.value === PaymentMethod.PIX;
  }
}
