import {Component, Input} from '@angular/core';
import {NgClass, NgIf} from "@angular/common";
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from "@angular/forms";
import {CartService} from "../../../../service/cart.service";
import {AuthenticationService} from "../../../../service/authentication.service";
import {OrderService} from "../../../../service/order.service";
import {PaymentService} from "../../../../service/payment.service";
import {SignupService} from "../../../../service/signup.service";

@Component({
  selector: 'app-user-info-step',
  standalone: true,
  imports: [
    NgIf,
    ReactiveFormsModule,
    NgClass
  ],
  templateUrl: './user-info-step.component.html',
  styleUrl: './user-info-step.component.css'
})
export class UserInfoStepComponent {
  @Input() checkoutForm!: FormGroup;

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
