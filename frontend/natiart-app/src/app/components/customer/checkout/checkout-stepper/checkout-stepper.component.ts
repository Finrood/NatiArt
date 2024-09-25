import {Component, Input} from '@angular/core';
import {NgClass} from "@angular/common";

@Component({
  selector: 'app-checkout-stepper',
  standalone: true,
  imports: [
    NgClass
  ],
  templateUrl: './checkout-stepper.component.html',
  styleUrl: './checkout-stepper.component.css'
})
export class CheckoutStepperComponent {
  @Input() currentStep: number = 1;
}
