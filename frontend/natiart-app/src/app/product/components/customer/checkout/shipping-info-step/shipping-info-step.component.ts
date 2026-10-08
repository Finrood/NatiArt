import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {FormGroup, ReactiveFormsModule} from '@angular/forms';
import {AddressFormComponent} from '../../../../../shared/components/address-form/address-form.component';

@Component({
  selector: 'app-shipping-info-step',
  imports: [ReactiveFormsModule, AddressFormComponent],
  templateUrl: './shipping-info-step.component.html',
  styleUrl: './shipping-info-step.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ShippingInfoStepComponent {
  @Input({required: true}) checkoutForm!: FormGroup;

  get shippingInfoFormGroup(): FormGroup {
    return this.checkoutForm.get('shippingInfo') as FormGroup;
  }
}
