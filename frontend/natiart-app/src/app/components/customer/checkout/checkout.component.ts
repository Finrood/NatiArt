import { Component, OnInit } from '@angular/core';
import { AsyncPipe, CurrencyPipe, NgClass, NgForOf, NgIf } from '@angular/common';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import { Observable } from 'rxjs';
import { CartItem } from '../../../models/CartItem.model';
import { User } from '../../../models/user.model';
import { CartService } from '../../../service/cart.service';
import { OrderService } from '../../../service/order.service';
import { AuthenticationService } from '../../../service/authentication.service';
import { RouterLink } from '@angular/router';
import { CepFormatDirective } from '../shipping-estimation/cep-format-directive.directive';
import { animate, style, transition, trigger } from '@angular/animations';
import { LoadingSpinnerComponent } from '../../shared/loading-spinner/loading-spinner.component';
import {PaymentService} from "../../../service/payment.service";
import {PaymentCreationRequest} from "../../../models/paymentCreationRequest.model";
import {PaymentCreationResponse} from "../../../models/paymentCreationResonse.model";
import {OrderSummaryComponent} from "./order-summary/order-summary.component";
import {CheckoutStepperComponent} from "./checkout-stepper/checkout-stepper.component";
import {UserInfoStepComponent} from "./user-info-step/user-info-step.component";
import {ShippingInfoStepComponent} from "./shipping-info-step/shipping-info-step.component";
import {PaymentInfoStepComponent} from "./payment-info-step/payment-info-step.component";
import {SignupService} from "../../../service/signup.service";
import {UserRegistration} from "../../../models/user-registration.model";
import {Profile} from "../../../models/profile.model";

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [
    NgIf,
    AsyncPipe,
    RouterLink,
    ReactiveFormsModule,
    FormsModule,
    OrderSummaryComponent,
    CheckoutStepperComponent,
    UserInfoStepComponent,
    ShippingInfoStepComponent,
    PaymentInfoStepComponent
  ],
  animations: [
    trigger('fadeIn', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('300ms', style({ opacity: 1 })),
      ]),
    ]),
    trigger('slideInRight', [
      transition(':enter', [
        style({ transform: 'translateX(100%)', opacity: 0 }),
        animate('300ms', style({ transform: 'translateX(0)', opacity: 1 })),
      ]),
    ]),
  ],
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.css'
})
export class CheckoutComponent implements OnInit {
  checkoutForm: FormGroup;
  currentStep = 1;
  errorMessage = '';
  cartItems$: Observable<CartItem[]>;
  cartTotal$: Observable<number>;
  isLoggedIn$: Observable<boolean>;
  currentUser$: Observable<User | null>;
  isLoading$: Observable<boolean>;
  sameShippingAsBilling = true;

  constructor(
    private fb: FormBuilder,
    private cartService: CartService,
    private authenticationService: AuthenticationService,
    private orderService: OrderService,
    private paymentService: PaymentService,
    private signupService: SignupService
  ) {
    this.checkoutForm = this.fb.group({
      userInfo: this.fb.group({
        firstname: ['', Validators.required],
        lastname: ['', Validators.required],
        cpf: ['', [Validators.required, Validators.pattern('[0-9]*')]],
        email: ['', [Validators.required, Validators.email]],
        phone: ['', Validators.pattern('[0-9]*')],
      }),
      shippingInfo: this.fb.group({
        country: ['Brazil', Validators.required],
        state: ['', Validators.required],
        city: ['', Validators.required],
        neighborhood: ['', Validators.required],
        zipCode: ['', Validators.required],
        street: ['', Validators.required],
        complement: [''],
      }),
      billingInfo: this.fb.group({
        country: ['Brazil', Validators.required],
        state: ['', Validators.required],
        city: ['', Validators.required],
        neighborhood: ['', Validators.required],
        zipCode: ['', Validators.required],
        street: ['', Validators.required],
        complement: [''],
      }),
      paymentInfo: this.fb.group({
        paymentMethod: ['', Validators.required],
        cardNumber: ['', Validators.required],
        expirationDate: ['', Validators.required],
        cvv: ['', Validators.required],
        pixKey: ['', Validators.required],
      }),
    });

    this.cartItems$ = this.cartService.getCartItems();
    this.cartTotal$ = this.cartService.getCartTotal();
    this.isLoggedIn$ = this.authenticationService.isLoggedIn$;
    this.currentUser$ = this.authenticationService.currentUser$;
    this.isLoading$ = this.orderService.orderProcessing$;
  }

  ngOnInit(): void {
    this.authenticationService.getCurrentUser();
    this.currentUser$.subscribe(user => {
      if (user) {
        this.checkoutForm.patchValue({
          userInfo: {
            firstname: user.profile.firstname,
            lastname: user.profile.lastname,
            email: user.username,
            phone: user.profile.phone,
          },
          shippingInfo: {
            country: user.profile.country,
            state: user.profile.state,
            city: user.profile.city,
            neighborhood: user.profile.neighborhood,
            zipCode: user.profile.zipCode,
            street: user.profile.street,
            complement: user.profile.complement,
          },
          billingInfo: {
            country: user.profile.country,
            state: user.profile.state,
            city: user.profile.city,
            neighborhood: user.profile.neighborhood,
            zipCode: user.profile.zipCode,
            street: user.profile.street,
            complement: user.profile.complement,
          },
        });
      }
    });
  }

  logFormState() {
    console.log('Form Value:', this.checkoutForm.value);
    console.log('Form Valid:', this.checkoutForm.valid);
    console.log('Form Errors:', this.checkoutForm.errors);
  }

  isCurrentStepValid(): boolean | undefined {
    switch (this.currentStep) {
      case 1:
        return this.checkoutForm.get('userInfo')?.valid;
      case 2:
        return this.checkoutForm.get('shippingInfo')?.valid;
      case 3:
        return this.checkoutForm.get('paymentInfo')?.valid;
      default:
        return true;
    }
  }

  onNextStep(): void {
    if (this.currentStep === 1) {
      if (this.checkoutForm.get('userInfo')?.invalid) {
        this.checkoutForm.get('userInfo')?.markAllAsTouched();
        return;
      }
    } else if (this.currentStep === 2) {
      if (this.checkoutForm.get('shippingInfo')?.invalid) {
        this.checkoutForm.get('shippingInfo')?.markAllAsTouched();
        return;
      }
      if (!this.sameShippingAsBilling) {
        if (this.checkoutForm.get('billingInfo')?.invalid) {
          this.checkoutForm.get('billingInfo')?.markAllAsTouched();
          return;
        }
      }
    }
    this.currentStep++;
    this.clearErrorMessage();
  }

  onPreviousStep(): void {
    this.currentStep--;
    this.clearErrorMessage();
  }

  private generateGuestPassword(): string {
    // You can generate a random password or use a fixed pattern for guest users
    return Math.random().toString(36).slice(-8); // Example random password generator
  }

  createUserIfGuestCheckout(): void {
    if (!this.isLoggedIn$) {
      const formValue = this.checkoutForm.get('userInfo')?.value;
      const profile: Profile = {
        firstname: formValue.firstname,
        lastname: formValue.lastname,
        cpf: '',
        phone: formValue.phone,
        country: formValue.country,
        state: formValue.state,
        city: formValue.city,
        neighborhood: formValue.neighborhood,
        zipCode: formValue.zipCode,
        street: formValue.street,
        complement: formValue.complement,
      };

      const userRegistration: UserRegistration = {
        username: formValue.email,
        password: 'temporaryPassword', // Handle this securely
        profile: profile,
      };

      this.signupService.registerUser(userRegistration).subscribe({
        next: () => {
          console.log('User registered as guest');
        },
        error: (error: any) => {
          this.setErrorMessage('Registration failed. Please try again.');
          console.error('Registration error:', error);
        }
      });
    }
  }

  processPixPayment(orderData: any) {
    const pixPaymentData: PaymentCreationRequest = {
      paymentProcessor: "ASAAS",
      customerId: "6218382",
      billingType: 'PIX',
      value: this.cartService.getCartTotalSnapshot(),
    };

    this.paymentService.createPixPayment(pixPaymentData).subscribe({
      next: (response: PaymentCreationResponse) => {
        this.paymentService.getPixQrCode(response.paymentId).subscribe({
          next: (qrCodeData) => {
            console.log(qrCodeData);
            // You might want to navigate to a confirmation page or show a modal here
          },
          error: (error) => {
            console.error('Error fetching PIX QR code:', error);
            this.setErrorMessage('Error generating PIX QR code. Please try again.');
          }
        });
      },
      error: (error) => {
        console.error('Error creating PIX payment:', error);
        this.setErrorMessage('Error creating PIX payment. Please try again.');
      }
    });
  }

  onSubmit(): void {
    if (this.checkoutForm.valid) {
      const orderData = {
        ...this.checkoutForm.value,
        //items: this.cartService.getCartItemsSnapshot()
      };

      const paymentMethod = this.checkoutForm.get('paymentInfo.paymentMethod')?.value;
      if (paymentMethod === 'credit_card' || paymentMethod === 'debit_card') {
        // Process credit/debit card payment
        // this.orderService.processCardPayment(orderData).subscribe({
        //   next: () => {
        //     // Handle successful order placement (e.g., navigate to confirmation page)
        //     console.log('Order placed successfully');
        //   },
        //   error: (error: any) => {
        //     // Handle error (e.g., show error message)
        //     console.error('Error placing order:', error);
        //   }
        // });
      } else if (paymentMethod === 'pix') {

        // Process PIX payment
        // this.orderService.processPixPayment(orderData).subscribe({
        //   next: () => {
        //     // Handle successful order placement (e.g., navigate to confirmation page)
        //     console.log('Order placed successfully');
        //   },
        //   error: (error: any) => {
        //     // Handle error (e.g., show error message)
        //     console.error('Error placing order:', error);
        //   }
        // });
      }
    }
  }

  private setErrorMessage(message: string): void {
    this.errorMessage = message;
  }

  private clearErrorMessage(): void {
    this.errorMessage = '';
  }
}
