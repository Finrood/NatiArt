import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {CommonModule, NgClass, NgForOf, NgIf} from "@angular/common";

interface Step {
  id: number;
  label: string;
}

@Component({
  selector: 'app-checkout-stepper',
  standalone: true,
  imports: [
    CommonModule,
    NgClass,
    NgIf,
    NgForOf
  ],
  templateUrl: './checkout-stepper.component.html',
  // styleUrls: ['./checkout-stepper.component.css'] // Keep if you have specific styles
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CheckoutStepperComponent {
  @Input() currentStep: number = 1;

  steps: Step[] = [
    { id: 1, label: 'Account' },
    { id: 2, label: 'Delivery' },
    { id: 3, label: 'Payment' }
  ];
}
