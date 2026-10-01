import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {FormGroup, ReactiveFormsModule} from "@angular/forms";
import {AddressFormComponent} from "../address-form/address-form.component";

@Component({
  selector: 'app-shipping-info-step',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    AddressFormComponent
],
  templateUrl: './shipping-info-step.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ShippingInfoStepComponent {
  @Input({ required: true }) checkoutForm!: FormGroup;

  get shippingInfoFormGroup(): FormGroup {
    return this.checkoutForm.get('shippingInfo') as FormGroup;
  }

}
