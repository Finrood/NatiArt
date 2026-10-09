import {ChangeDetectionStrategy, Component, Input, input} from '@angular/core';
import {RouterLink} from '@angular/router';

import {FormGroup, ReactiveFormsModule} from "@angular/forms";
import {
  NatiartFormFieldComponent
} from "../../../../../shared/components/natiart-form-field/natiart-form-field.component";
import {CpfFormatDirective} from "../../../../../directory/directive/cpf-format-directive.directive";
import {PhoneFormatBrazilDirective} from "../../../../../directory/directive/phone-format-brazil.directive";

@Component({
  selector: 'app-user-info-step',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    NatiartFormFieldComponent,
    CpfFormatDirective,
    PhoneFormatBrazilDirective
],
  templateUrl: './user-info-step.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserInfoStepComponent {
  @Input({ required: true }) checkoutForm!: FormGroup;
  readonly accountCheckout = input<boolean>(false);

  get userInfoGroup(): FormGroup {
    return this.checkoutForm.get('userInfo') as FormGroup;
  }

}
