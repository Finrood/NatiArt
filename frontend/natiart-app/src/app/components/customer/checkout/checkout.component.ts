import {Component, OnInit} from '@angular/core';
import {AsyncPipe, CurrencyPipe, NgClass, NgForOf, NgIf} from "@angular/common";
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from "@angular/forms";
import {Observable} from "rxjs";
import {CartItem} from "../../../models/CartItem.model";
import {User} from "../../../models/user.model";
import {CartService} from "../../../service/cart.service";
import {OrderService} from "../../../service/order.service";
import {AuthenticationService} from "../../../service/authentication.service";
import {RouterLink} from "@angular/router";
import {CepFormatDirective} from "../shipping-estimation/cep-format-directive.directive";
import {animate, style, transition, trigger} from "@angular/animations";
import {finalize} from "rxjs/operators";
import {SignupService, ViaCEPResponse} from "../../../service/signup.service";
import {LoadingSpinnerComponent} from "../../shared/loading-spinner/loading-spinner.component";

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [
    NgIf,
    AsyncPipe,
    RouterLink,
    ReactiveFormsModule,
    CurrencyPipe,
    NgForOf,
    CepFormatDirective,
    NgClass,
    LoadingSpinnerComponent
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
  isLoadingAddress = false;
  cartItems$: Observable<CartItem[]>;
  cartTotal$: Observable<number>;
  isLoggedIn$: Observable<boolean>;
  currentUser$: Observable<User | null>;
  isLoading$: Observable<boolean>;

  constructor(
    private fb: FormBuilder,
    private cartService: CartService,
    private authenticationService: AuthenticationService,
    private orderService: OrderService,
    private signupService: SignupService
  ) {
    this.checkoutForm = this.fb.group({
      userInfo: this.fb.group({
        firstname: ['', Validators.required],
        lastname: ['', Validators.required],
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
      })
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
          }
        });
      }
    });
  }

  onNextStep(): void {
    if (this.checkoutForm.get('userInfo')?.invalid) {
      this.checkoutForm.get('userInfo')?.markAllAsTouched();
      return;
    }
    this.currentStep = 2;
    this.clearErrorMessage();
  }

  onPreviousStep(): void {
    this.currentStep = 1;
    this.clearErrorMessage();
  }

  onSubmit(): void {
    if (this.checkoutForm.valid) {
      const orderData = {
        ...this.checkoutForm.value,
        //items: this.cartService.getCartItemsSnapshot()
      };
      this.orderService.createOrder(orderData).subscribe({
        next: () => {
          // Handle successful order placement (e.g., navigate to confirmation page)
          console.log('Order placed successfully');
        },
        error: (error: any) => {
        // Handle error (e.g., show error message)
        console.error('Error placing order:', error);
      }
    });
    }
  }

  getFieldError(fieldName: string): string {
    const field = this.checkoutForm.get(fieldName);
    if (field?.invalid && (field.dirty || field.touched)) {
      if (field.errors?.['required']) return 'This field is required.';
      if (field.errors?.['email']) return 'Please enter a valid email address.';
      if (field.errors?.['pattern']) return 'Please enter a valid phone number.';
    }
    return '';
  }

  private setErrorMessage(message: string): void {
    this.errorMessage = message;
  }

  private clearErrorMessage(): void {
    this.errorMessage = '';
  }

  onZipCodeChange(): void {
    const zipCode = this.checkoutForm.get('shippingInfo.zipCode')?.value?.replace(/\D/g, '');
    if (zipCode?.length !== 8) {
      return;
    }

    this.isLoadingAddress = true;
    this.signupService.getAddressFromZipCode(zipCode)
      .pipe(finalize(() => this.isLoadingAddress = false))
      .subscribe({
        next: (data: ViaCEPResponse) => {
          this.checkoutForm.patchValue({
            shippingInfo: {
              street: data.logradouro,
              city: data.localidade,
              neighborhood: data.bairro,
              state: data.uf,
              country: "Brazil",
            },
          });
        },
        error: () => {
          this.setErrorMessage('Error fetching address. Please enter manually.');
        }
      });
  }
}
