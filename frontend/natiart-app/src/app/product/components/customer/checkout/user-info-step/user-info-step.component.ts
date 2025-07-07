import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {CommonModule} from "@angular/common";
import {FormGroup, ReactiveFormsModule} from "@angular/forms";
import {
  NatiartFormFieldComponent
} from "../../../../../shared/components/natiart-form-field/natiart-form-field.component";
import {CpfFormatDirective} from "../../../../../directory/directive/cpf-format-directive.directive";
import {PhoneFormatBrazilDirective} from "../../../../../directory/directive/phone-format-brazil.directive";

@Component({
  selector: 'app-user-info-step',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    NatiartFormFieldComponent,
    CpfFormatDirective,
    PhoneFormatBrazilDirective
  ],
  templateUrl: './user-info-step.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserInfoStepComponent {
  @Input({ required: true }) checkoutForm!: FormGroup; // Parent form groupµµ

  get userInfoGroup(): FormGroup { // Helper getter
    return this.checkoutForm.get('userInfo') as FormGroup;
  }

  // No need for formFields array if using NatiartFormFieldComponent correctly
}
