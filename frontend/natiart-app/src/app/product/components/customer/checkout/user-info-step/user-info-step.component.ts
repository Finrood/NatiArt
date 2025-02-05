import {Component, Input} from '@angular/core';
import {NgClass, NgForOf, NgIf} from "@angular/common";
import {FormGroup, ReactiveFormsModule} from "@angular/forms";

@Component({
    selector: 'app-user-info-step',
    imports: [
        NgIf,
        ReactiveFormsModule,
        NgClass,
        NgForOf
    ],
    templateUrl: './user-info-step.component.html',
    styleUrls: ['./user-info-step.component.css'] // Fixed here
})
export class UserInfoStepComponent {
  @Input() checkoutForm!: FormGroup;

  formFields = [
    {
      id: 'email',
      name: 'email',
      label: 'Email address',
      type: 'email',
      placeholder: 'you@yourbest.com',
      required: true
    },
    {id: 'firstname', name: 'firstname', label: 'First Name', type: 'text', placeholder: 'John', required: true},
    {id: 'lastname', name: 'lastname', label: 'Last Name', type: 'text', placeholder: 'Doe', required: true},
    {id: 'cpf', name: 'cpf', label: 'CPF', type: 'text', placeholder: '000.000.000-11', required: true},
    {id: 'phone', name: 'phone', label: 'Phone', type: 'tel', placeholder: '(XX) XXXXX-XXXX', required: false}
  ];

  getFieldError(fieldName: string): string {
    const field = this.checkoutForm.get(fieldName);
    if (field?.invalid && (field.dirty || field.touched)) {
      if (field.errors?.['required']) return 'This field is required.';
      if (field.errors?.['email']) return 'Please enter a valid email address.';
      if (field.errors?.['pattern']) return 'Please enter a valid phone number or card number.';
    }
    return '';
  }
}
