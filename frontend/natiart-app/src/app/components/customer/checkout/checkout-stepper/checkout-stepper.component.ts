import {Component, Input} from '@angular/core';
import {NgClass, NgForOf, NgIf} from "@angular/common";

@Component({
  selector: 'app-checkout-stepper',
  standalone: true,
  imports: [
    NgClass,
    NgIf,
    NgForOf
  ],
  templateUrl: './checkout-stepper.component.html',
  styleUrl: './checkout-stepper.component.css'
})
export class CheckoutStepperComponent {
  @Input() currentStep: number = 1;
}
