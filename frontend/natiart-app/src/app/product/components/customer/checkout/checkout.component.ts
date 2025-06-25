import {ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit} from '@angular/core';
import {AsyncPipe, CommonModule, NgIf} from '@angular/common';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {catchError, firstValueFrom, map, Observable, Subject, throwError} from 'rxjs';
import {CartItem} from '../../../models/CartItem.model';
import {CartService} from '../../../service/cart.service';
import {OrderService} from '../../../service/order.service';
import {Router, RouterLink} from '@angular/router';
import {animate, style, transition, trigger} from '@angular/animations';
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
import {SignupService} from "../../../../directory/service/signup.service";
import {Profile} from "../../../../directory/models/profile.model";
import {UserRegistration} from "../../../../directory/models/user-registration.model";

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [
    NgIf,
    AsyncPipe,
    RouterLink,
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    OrderSummaryComponent,
    UserInfoStepComponent,
    ShippingInfoStepComponent,
    PaymentInfoStepComponent,
    LoadingSpinnerComponent
  ],
  animations: [
    trigger('fadeIn', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(10px)' }),
        animate('300ms ease-out', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
      transition(':leave', [
        animate('300ms ease-in', style({ opacity: 0, transform: 'translateY(10px)' }))
      ])
    ]),
  ],
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CheckoutComponent implements OnInit, OnDestroy {
  checkoutForm: FormGroup;
  errorMessage = '';
  cartItems$: Observable<CartItem[]>;
  cartTotal$: Observable<number>;
  isLoggedIn$: Observable<boolean>;
  currentUser$: Observable<User | null>;
  isLoading$: Observable<boolean>;
  sameShippingAsBilling = true;

  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private cartService: CartService,
    private authenticationService: AuthenticationService,
    private orderService: OrderService,
    private paymentService: PaymentService,
    private signupService: SignupService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {
    this.checkoutForm = this.fb.group({
      userInfo: this.fb.group({
        firstname: ['', Validators.required],
        lastname: ['', Validators.required],
        cpf: ['', [Validators.required, Validators.pattern('[0-9.]*\-[0-9]*')]],
        email: ['', [Validators.required, Validators.email]],
        phone: ['', Validators.pattern('[()0-9 -]*')],
      }),
      shippingInfo: this.fb.group({
        country: ['Brazil', Validators.required],
        state: ['', Validators.required],
        city: ['', Validators.required],
        neighborhood: ['', Validators.required],
        zipCode: ['', [Validators.required, Validators.pattern(/^\d{5}-\d{3}$/)]],
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
      cardNumberCtrl?.setValidators([Validators.required, Validators.pattern('^[0-9]{13,19}
)]);
      expirationDateCtrl?.setValidators([Validators.required, Validators.pattern('^(0[1-9]|1[0-2])\\/?([0-9]{2})
)]);
      cvvCtrl?.setValidators([Validators.required, Validators.pattern('^[0-9]{3,4}
)]);
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
          const userInfo = this.checkoutForm.get('userInfo')?.value;
          const shippingInfo = this.checkoutForm.get('shippingInfo')?.value;

          const profile: Profile = {
            firstname: userInfo.firstname,
            lastname: userInfo.lastname,
            cpf: userInfo.cpf.replace(/\D/g, ''),
            phone: userInfo.phone.replace(/\D/g, ''),
            country: shippingInfo.country,
            state: shippingInfo.state,
            city: shippingInfo.city,
            neighborhood: shippingInfo.neighborhood,
            zipCode: shippingInfo.zipCode.replace(/\D/g, ''),
            street: shippingInfo.street,
            complement: shippingInfo.complement,
          };

          const userRegistration: UserRegistration = {
            username: userInfo.email,
            password: this.generateRandomPassword(),
            profile: profile,
          };
          this.setInfoMessage('Creating a temporary account to process your order...');
          return this.signupService.registerGhostUser(userRegistration).pipe(
            switchMap(() => {
              this.clearInfoMessage();
              return this.authenticationService.login({ username: userInfo.email, password: userRegistration.password });
            }),
            switchMap(() => this.currentUser$),
            map(user => {
              if (!user) throw new Error('Ghost user registration or login failed.');
              return user;
            }),
            catchError(error => {
              this.clearInfoMessage();
              this.setErrorMessage('Guest checkout setup failed. Please try again or register.');
              console.error('Guest checkout error:', error);
              return throwError(() => error);
            })
          );
        } else {
          return this.currentUser$.pipe(map(user => {
            if (!user) throw new Error('No logged-in user found.');
            return user;
          }));
        }
      })
    );
  }

  async onProcessPixPayment() {
    this.clearErrorMessage();
    try {
      let user = await firstValueFrom(this.createUserIfGuestCheckout());

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

      this.router.navigate(['/pix-payment', paymentResponse.paymentId]);

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

    try {
      let user = await firstValueFrom(this.createUserIfGuestCheckout().pipe(takeUntil(this.destroy$)));
      if (!user) return;

      const paymentMethod = this.checkoutForm.get('paymentInfo.paymentMethod')?.value;

      if (paymentMethod === PaymentMethod.PIX) {
        await this.onProcessPixPayment();
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
    }
    this.cdr.detectChanges();
  }

  private generateRandomPassword(): string {
    return Math.random().toString(36).slice(-12);
  }

  private setInfoMessage(message: string): void {
    this.errorMessage = `INFO: ${message}`;
    this.cdr.detectChanges();
  }
  private clearInfoMessage(): void {
    if(this.errorMessage.startsWith("INFO:")) {
      this.errorMessage = "";
    }
    this.cdr.detectChanges();
  }


  private setErrorMessage(message: string): void {
    this.errorMessage = message;
    this.cdr.detectChanges();
    setTimeout(() => this.clearErrorMessage(), 7000);
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
