import {ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit} from '@angular/core';
import { AsyncPipe, CommonModule } from '@angular/common';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {EmptyError, firstValueFrom, map, Observable, Subject, throwError} from 'rxjs';
import {CartItem} from '../../../models/CartItem.model';
import {CartService} from '../../../service/cart.service';
import {OrderService} from '../../../service/order.service';
import {Router} from '@angular/router';
import {PaymentService} from "../../../service/payment.service";
import {PaymentCreationRequest} from "../../../models/paymentCreationRequest.model";
import {OrderSummaryComponent} from "./order-summary/order-summary.component";
import {UserInfoStepComponent} from "./user-info-step/user-info-step.component";
import {ShippingInfoStepComponent} from "./shipping-info-step/shipping-info-step.component";
import {PaymentInfoStepComponent} from "./payment-info-step/payment-info-step.component";
import {switchMap, takeUntil, tap} from "rxjs/operators";
import {PaymentMethod} from "../../../models/paymentMethod.model";
import {LoadingSpinnerComponent} from "../../../../shared/components/shared/loading-spinner/loading-spinner.component";
import {User} from "../../../../directory/models/user.model";
import {AuthenticationService} from "../../../../directory/service/authentication.service";
import {CustomCpfValidators} from "../../../../directory/validator/CustomCpfValidators";
import {CustomCepValidators} from "../../../../directory/validator/CustomCepValidators";
import {ButtonComponent} from "../../../../shared/components/button.component";

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [
    AsyncPipe,
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    OrderSummaryComponent,
    UserInfoStepComponent,
    ShippingInfoStepComponent,
    PaymentInfoStepComponent,
    LoadingSpinnerComponent,
    ButtonComponent
],
  templateUrl: './checkout.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CheckoutComponent implements OnInit, OnDestroy {
  checkoutForm: FormGroup;
  errorMessage = '';
  infoMessage = '';
  isSubmitting: boolean = false;
  cartItems$: Observable<CartItem[]>;
  cartTotal$: Observable<number>;
  isLoggedIn$: Observable<boolean>;
  currentUser$: Observable<User | null>;
  isLoading$: Observable<boolean>;
  sameShippingAsBilling = true;
  currentStep = 1;

  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private cartService: CartService,
    private authenticationService: AuthenticationService,
    private orderService: OrderService,
    private paymentService: PaymentService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {
    this.checkoutForm = this.fb.group({
      userInfo: this.fb.group({
        firstname: ['', Validators.required],
        lastname: ['', Validators.required],
        cpf: ['', [Validators.required, CustomCpfValidators.validCpf()]],
        email: ['', [Validators.required, Validators.email]],
        phone: ['', Validators.pattern('[()0-9 -]*')],
      }),
      shippingInfo: this.fb.group({
        country: ['Brazil', Validators.required],
        state: ['', Validators.required],
        city: ['', Validators.required],
        neighborhood: ['', Validators.required],
        zipCode: ['', [Validators.required, CustomCepValidators.validCep()]],
        street: ['', Validators.required],
        complement: [''],
      }),
      billingInfo: this.fb.group({
        country: ['Brazil'],
        state: [''],
        city: [''],
        neighborhood: [''],
        zipCode: ['', Validators.pattern(/^\d{5}-\d{3}$/)],
        street: [''],
        complement: [''],
      }),
      paymentInfo: this.fb.group({
        paymentMethod: ['', Validators.required],
        cardNumber: [''],
        expirationDate: [''],
        cvv: [''],
      }),
    });

    this.cartItems$ = this.cartService.getCartItems();
    this.cartTotal$ = this.cartService.getCartTotal();
    this.isLoggedIn$ = this.authenticationService.isLoggedIn$;
    this.currentUser$ = this.authenticationService.currentUser$;
    this.isLoading$ = this.orderService.orderProcessing$;
  }

  nextStep() {
    if (this.currentStep === 1) {
      this.checkoutForm.get('userInfo')?.markAllAsTouched();
      if (this.checkoutForm.get('userInfo')?.invalid) {
        return;
      }
    } else if (this.currentStep === 2) {
      this.checkoutForm.get('shippingInfo')?.markAllAsTouched();
      if (this.checkoutForm.get('shippingInfo')?.invalid) {
        return;
      }
    }

    if (this.currentStep < 3) {
      this.currentStep++;
    }
  }

  prevStep() {
    if (this.currentStep > 1) {
      this.currentStep--;
    }
  }

  private formatCpf(cpf: string): string {
    const cleanValue = cpf.replace(/\D/g, '').slice(0, 11);
    if (cleanValue.length <= 3) {
      return cleanValue;
    } else if (cleanValue.length <= 6) {
      return `${cleanValue.slice(0, 3)}.${cleanValue.slice(3)}`;
    } else if (cleanValue.length <= 9) {
      return `${cleanValue.slice(0, 3)}.${cleanValue.slice(3, 6)}.${cleanValue.slice(6)}`;
    } else {
      return `${cleanValue.slice(0, 3)}.${cleanValue.slice(3, 6)}.${cleanValue.slice(6, 9)}-${cleanValue.slice(9)}`;
    }
  }

  ngOnInit(): void {
    this.authenticationService.fetchCurrentUser()
      .pipe(takeUntil(this.destroy$))
      .subscribe();

    this.currentUser$
      .pipe(
        takeUntil(this.destroy$),
        tap(user => {
          if (user && user.profile) {
            this.checkoutForm.patchValue({
              userInfo: {
                firstname: user.profile.firstname,
                lastname: user.profile.lastname,
                email: user.username,
                cpf: this.formatCpf(user.profile.cpf),
                phone: user.profile.phone,
              },
              shippingInfo: {
                country: user.profile.country || 'Brazil',
                state: user.profile.state,
                city: user.profile.city,
                neighborhood: user.profile.neighborhood,
                zipCode: user.profile.zipCode,
                street: user.profile.street,
                complement: user.profile.complement,
              },
            });
            if (this.sameShippingAsBilling) {
              this.checkoutForm.get('billingInfo')?.patchValue(this.checkoutForm.get('shippingInfo')?.value);
            }

            // Mark controls as touched if they are invalid after pre-filling
            if (this.checkoutForm.get('userInfo')?.invalid) {
              this.checkoutForm.get('userInfo')?.markAllAsTouched();
            }
            if (this.checkoutForm.get('shippingInfo')?.invalid) {
              this.checkoutForm.get('shippingInfo')?.markAllAsTouched();
            }
          }
        })
      )
      .subscribe();

    this.updatePaymentValidators();
    this.checkoutForm.get('paymentInfo.paymentMethod')?.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.updatePaymentValidators());
  }

  onSameShippingChange(isSame: boolean): void {
    this.sameShippingAsBilling = isSame;
    this.cdr.detectChanges();
  }

  updatePaymentValidators(): void {
    const paymentMethod = this.checkoutForm.get('paymentInfo.paymentMethod')?.value;
    const cardNumberCtrl = this.checkoutForm.get('paymentInfo.cardNumber');
    const expirationDateCtrl = this.checkoutForm.get('paymentInfo.expirationDate');
    const cvvCtrl = this.checkoutForm.get('paymentInfo.cvv');

    if (paymentMethod === PaymentMethod.CREDIT_CARD || paymentMethod === PaymentMethod.DEBIT_CARD) {
      cardNumberCtrl?.setValidators([Validators.required, Validators.pattern('^[0-9]{13,19}')]);
      expirationDateCtrl?.setValidators([Validators.required, Validators.pattern('^(0[1-9]|1[0-2])\/?([0-9]{2})')]);
      cvvCtrl?.setValidators([Validators.required, Validators.pattern('^[0-9]{3,4}')]);
    } else {
      cardNumberCtrl?.clearValidators();
      expirationDateCtrl?.clearValidators();
      cvvCtrl?.clearValidators();
    }
    cardNumberCtrl?.updateValueAndValidity({ emitEvent: false });
    expirationDateCtrl?.updateValueAndValidity({ emitEvent: false });
    cvvCtrl?.updateValueAndValidity({ emitEvent: false });
  }

  createUserIfGuestCheckout(): Observable<User> {
    return this.isLoggedIn$.pipe(
      switchMap(isLoggedIn => {
        if (!isLoggedIn) {
          this.setErrorMessage('Please sign in or register before checking out.');
          return throwError(() => new Error('Guest checkout requires an authenticated account.'));
        } else {
          return this.currentUser$.pipe(map(user => {
            if (!user) throw new Error('No logged-in user found.');
            return user;
          }));
        }
      })
    );
  }

  async onProcessPixPayment(user: User): Promise<void> {
    this.clearErrorMessage();
    try {
      if (!user || !user.externalId) {
        this.setErrorMessage('Could not retrieve customer ID for payment. Please try again.');
        return;
      }

      const pixPaymentData: PaymentCreationRequest = {
        paymentProcessor: 'ASAAS',
        customerId: user.externalId,
        billingType: PaymentMethod.PIX,
        value: this.cartService.getCartTotalSnapshot(),
      };

      const paymentResponse = await firstValueFrom(
        this.paymentService.createPixPayment(pixPaymentData)
      );

      const paymentId: string | undefined = paymentResponse?.paymentId;
      if (!paymentId) {
        this.setErrorMessage('Could not process PIX payment. Please try again.');
        return;
      }

      this.router.navigate(['/pix-payment', paymentId]);

    } catch (error) {
      console.error('Error processing PIX payment:', error);
      this.setErrorMessage('Could not process PIX payment. Please try again.');
    }
    this.cdr.detectChanges();
  }

  async onSubmit(): Promise<void> {
    this.clearErrorMessage();
    if (this.checkoutForm.invalid) {
      this.checkoutForm.markAllAsTouched();
      this.setErrorMessage('Please correct the errors in the form.');
      return;
    }
    if (this.isSubmitting) {
      return;
    }
    this.isSubmitting = true;

    try {
      let user: User;
      try {
        user = await firstValueFrom(this.createUserIfGuestCheckout().pipe(takeUntil(this.destroy$)));
      } catch (error) {
        if (error instanceof EmptyError) {
          return;
        }
        throw error;
      }
      if (!user) return;

      const paymentMethod = this.checkoutForm.get('paymentInfo.paymentMethod')?.value;

      if (paymentMethod === PaymentMethod.PIX) {
        await this.onProcessPixPayment(user);
        return;
      }

      if (paymentMethod === PaymentMethod.CREDIT_CARD || paymentMethod === PaymentMethod.DEBIT_CARD) {
        this.setInfoMessage('Processing card payment...');
        this.setErrorMessage('Card payment is not yet implemented.');
        this.clearInfoMessage();
        return;
      }

      this.setErrorMessage('Invalid payment method selected.');

    } catch (error) {
      console.error('Order submission error:', error);
      if (!this.errorMessage) {
        this.setErrorMessage('An unexpected error occurred during checkout.');
      }
    } finally {
      this.isSubmitting = false;
    }
    this.cdr.detectChanges();
  }

  private setInfoMessage(message: string): void {
    this.infoMessage = message;
    this.cdr.detectChanges();
  }
  private clearInfoMessage(): void {
    this.infoMessage = '';
    this.cdr.detectChanges();
  }

  private setErrorMessage(message: string): void {
    this.errorMessage = message;
    this.cdr.detectChanges();
  }

  dismissError(): void {
    this.errorMessage = '';
    this.cdr.detectChanges();
  }

  private clearErrorMessage(): void {
    this.errorMessage = '';
    this.cdr.detectChanges();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
