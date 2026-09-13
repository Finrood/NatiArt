import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {FormGroup, ReactiveFormsModule} from "@angular/forms";

import {
  NatiartFormFieldComponent
} from "../../../../../shared/components/natiart-form-field/natiart-form-field.component";
import {PaymentMethod} from "../../../../models/paymentMethod.model";

@Component({
  selector: 'app-payment-info-step',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    NatiartFormFieldComponent,
    NatiartFormFieldComponent
],
  templateUrl: './payment-info-step.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PaymentInfoStepComponent {
  @Input({ required: true }) checkoutForm!: FormGroup;

  paymentMethods = [{value: PaymentMethod.PIX, label: 'PIX'}];

  get paymentInfoGroup(): FormGroup { // Helper getter
    return this.checkoutForm.get('paymentInfo') as FormGroup;
  }

  isPixSelected(): boolean {
    return this.checkoutForm.get('paymentInfo.paymentMethod')?.value === PaymentMethod.PIX;
  }
}
