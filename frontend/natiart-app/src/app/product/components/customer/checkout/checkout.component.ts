import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnDestroy, OnInit} from '@angular/core';
import { AsyncPipe, CommonModule } from '@angular/common';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {EmptyError, firstValueFrom, map, Observable, Subject, throwError} from 'rxjs';
import {CartItem} from '../../../models/CartItem.model';
import {OrderDto} from '../../../models/order.model';
import {CartService, PurchasedCartLine} from '../../../service/cart.service';
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
import {reportError} from '../../../../shared/service/error-reporting.service';

interface CheckoutAttempt {
  username: string;
  fingerprint: string;
  orderRequest: OrderDto;
  purchasedCartLines: Array<{cartItemId: string; quantity: number}>;
  orderIdempotencyKey: string;
  paymentIdempotencyKey: string;
  currentOrder: OrderDto | null;
  paymentId: string | null;
}

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
  private readonly _fb: FormBuilder = inject(FormBuilder);
  private readonly _cartService: CartService = inject(CartService);
  private readonly _authenticationService: AuthenticationService = inject(AuthenticationService);
  private readonly _orderService: OrderService = inject(OrderService);
  private readonly _paymentService: PaymentService = inject(PaymentService);
  private readonly _router: Router = inject(Router);
  private readonly _cdr: ChangeDetectorRef = inject(ChangeDetectorRef);

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

  private currentOrder: OrderDto | null = null;
  private orderRequest: OrderDto | null = null;
  private purchasedCartLines: Array<{cartItemId: string; quantity: number}> = [];
  private paymentId: string | null = null;
  private checkoutFingerprint: string | null = null;
  private hasPrefilledCurrentUser = false;
  private restoredForUsername: string | null = null;
  private attemptStorageFailed = false;
  private destroyed = false;
  private readonly attemptStoragePrefix = 'natiart-checkout-attempt:';
  private orderIdempotencyKey: string = crypto.randomUUID();
  private paymentIdempotencyKey: string = crypto.randomUUID();

  get hasSavedAttempt(): boolean {
    return this.orderRequest !== null;
  }

  private destroy$ = new Subject<void>();

  constructor() {
    this.checkoutForm = this._fb.group({
      userInfo: this._fb.group({
        firstname: ['', Validators.required],
        lastname: ['', Validators.required],
        cpf: ['', [Validators.required, CustomCpfValidators.validCpf()]],
        email: ['', [Validators.required, Validators.email]],
        phone: ['', Validators.pattern('[()0-9 -]*')],
      }),
      shippingInfo: this._fb.group({
        country: ['Brazil', Validators.required],
        state: ['', Validators.required],
        city: ['', Validators.required],
        neighborhood: ['', Validators.required],
        zipCode: ['', [Validators.required, CustomCepValidators.validCep()]],
        street: ['', Validators.required],
        complement: [''],
      }),
      billingInfo: this._fb.group({
        country: ['Brazil'],
        state: [''],
        city: [''],
        neighborhood: [''],
        zipCode: ['', Validators.pattern(/^\d{5}-\d{3}$/)],
        street: [''],
        complement: [''],
      }),
      paymentInfo: this._fb.group({
        paymentMethod: ['', Validators.required],
      }),
    });

    this.cartItems$ = this._cartService.getCartItems();
    this.cartTotal$ = this._cartService.getCartTotal();
    this.isLoggedIn$ = this._authenticationService.isLoggedIn$;
    this.currentUser$ = this._authenticationService.currentUser$;
    this.isLoading$ = this._orderService.orderProcessing$;
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
    this._authenticationService.fetchCurrentUser()
      .pipe(takeUntil(this.destroy$))
      .subscribe();

    this.currentUser$
      .pipe(
        takeUntil(this.destroy$),
        tap(user => {
          if (user && this.restoredForUsername !== user.username) {
            this.restoredForUsername = user.username;
            this.hasPrefilledCurrentUser = false;
            this.restoreAttempt(user.username);
          }
          if (user && user.profile && !this.hasPrefilledCurrentUser && !this.hasSavedAttempt) {
            this.hasPrefilledCurrentUser = true;
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
    this._cdr.detectChanges();
  }

  updatePaymentValidators(): void {
    // The storefront currently offers only the server-backed PIX flow.
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
      if (this.destroyed || this.attemptStorageFailed) {
        return;
      }
      if (!user || !user.externalId) {
        this.setErrorMessage('Could not retrieve customer ID for payment. Please try again.');
        return;
      }

      if (!this.orderRequest) {
        const orderRequest = this.buildOrderRequest();
        this.orderRequest = orderRequest;
        this.purchasedCartLines = this._cartService.getCartItemsSnapshot().map(
          (item: CartItem) => ({cartItemId: item.cartItemId, quantity: item.quantity}));
        this.checkoutFingerprint = JSON.stringify(orderRequest);
        this.orderIdempotencyKey = crypto.randomUUID();
        this.paymentIdempotencyKey = crypto.randomUUID();
        // Save the immutable payload and keys before sending anything. A lost
        // response can then replay the same order without reserving stock again.
        if (!this.persistAttempt(user.username)) {
          this.orderRequest = null;
          this.checkoutFingerprint = null;
          this.setErrorMessage('Could not save your checkout. Please free browser storage and try again.');
          return;
        }
      }

      this.setInfoMessage(this.currentOrder ? 'Checking your saved order...' : 'Creating your order...');
      const order = await firstValueFrom(this._orderService
        .createOrder(this.orderRequest, this.orderIdempotencyKey)
        .pipe(takeUntil(this.destroy$)));
      if (this.destroyed) {
        return;
      }
      this.clearInfoMessage();
      if (!order?.id || order.totalAmount == null) {
        this.setErrorMessage('Could not confirm your order. Please try again.');
        return;
      }
      this.currentOrder = order;
      this.persistAttempt(user.username);

      if (order.status === 'CANCELLED' || ['PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED'].includes(order.status ?? '')) {
        if (this.clearPersistedAttempt(user.username)) {
          this.resetAttempt();
        }
        this.setInfoMessage(order.status === 'CANCELLED'
          ? 'The saved order was cancelled. You can start a new checkout.'
          : 'The saved order has already been paid.');
        return;
      }

      if (this.paymentId) {
        const payment = await firstValueFrom(this._paymentService
          .getPaymentStatus(this.paymentId)
          .pipe(takeUntil(this.destroy$)));
        if (this.destroyed) {
          return;
        }
        if (payment.status === 'COMPLETED') {
          if (this.clearPersistedAttempt(user.username)) {
            this.resetAttempt();
          }
          this.setInfoMessage('Your PIX payment has already completed.');
          return;
        }
        await this.navigateToPix(this.paymentId);
        return;
      }

      const pixPaymentData: PaymentCreationRequest = {
        paymentProcessor: 'ASAAS',
        customerId: user.externalId,
        billingType: PaymentMethod.PIX,
        orderId: order.id,
        value: order.totalAmount,
      };

      const paymentResponse = await firstValueFrom(
        this._paymentService.createPixPayment(pixPaymentData, this.paymentIdempotencyKey)
          .pipe(takeUntil(this.destroy$))
      );

      if (this.destroyed) {
        return;
      }

      this.paymentId = paymentResponse?.paymentId ?? null;
      if (!this.paymentId) {
        this.setErrorMessage('Could not process PIX payment. Please try again.');
        return;
      }
      this.persistAttempt(user.username);
      await this.navigateToPix(this.paymentId);

    } catch (error) {
      if (this.destroyed) {
        return;
      }
      if (error instanceof EmptyError) {
        return;
      }
      reportError('payment', error);
      this.setErrorMessage('Could not confirm your saved checkout. Please retry; your attempt has been kept.');
    }
    if (!this.destroyed) {
      this._cdr.detectChanges();
    }
  }

  private async navigateToPix(paymentId: string): Promise<void> {
    if (this.destroyed) {
      return;
    }
    const navigated = await this._router.navigate(['/pix-payment', paymentId]);
    if (!this.destroyed && !navigated) {
      this.setErrorMessage('Could not open the payment page. Your checkout is saved; please try again.');
    }
  }

  private buildOrderRequest(): OrderDto {
    const userInfo = this.checkoutForm.get('userInfo')?.getRawValue();
    const shippingInfo = this.checkoutForm.get('shippingInfo')?.getRawValue();
    const items = this._cartService.getCartItemsSnapshot().map(item => {
      if (!item.product.id) {
        throw new Error('A cart item is missing its product identifier.');
      }
      return {productId: item.product.id, quantity: item.quantity};
    });

    if (items.length === 0) {
      throw new Error('Cannot create an order from an empty cart.');
    }

    return {
      firstname: userInfo.firstname,
      lastname: userInfo.lastname,
      email: userInfo.email,
      phone: userInfo.phone,
      country: shippingInfo.country,
      state: shippingInfo.state,
      city: shippingInfo.city,
      neighborhood: shippingInfo.neighborhood,
      zipCode: shippingInfo.zipCode.replace(/\D/g, ''),
      street: shippingInfo.street,
      complement: shippingInfo.complement,
      items,
      deliveryAmount: 0,
    };
  }

  async onSubmit(): Promise<void> {
    if (this.destroyed) {
      return;
    }
    if (this.attemptStorageFailed) {
      this.setErrorMessage('Your saved checkout needs assistance before another order can be started.');
      return;
    }
    this.clearErrorMessage();
    if (this.checkoutForm.invalid && !this.hasSavedAttempt) {
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
      if (this.destroyed || !user) return;

      const paymentMethod = this.checkoutForm.get('paymentInfo.paymentMethod')?.value;

      if (this.hasSavedAttempt || paymentMethod === PaymentMethod.PIX) {
        await this.onProcessPixPayment(user);
        return;
      }

      this.setErrorMessage('Invalid payment method selected.');

    } catch (error) {
      if (this.destroyed) {
        return;
      }
      reportError('checkout', error);
      if (!this.errorMessage) {
        this.setErrorMessage('An unexpected error occurred during checkout.');
      }
    } finally {
      this.isSubmitting = false;
      if (!this.destroyed) {
        this._cdr.detectChanges();
      }
    }
  }

  private setInfoMessage(message: string): void {
    if (this.destroyed) return;
    this.infoMessage = message;
    this._cdr.detectChanges();
  }
  private clearInfoMessage(): void {
    if (this.destroyed) return;
    this.infoMessage = '';
    this._cdr.detectChanges();
  }

  private setErrorMessage(message: string): void {
    if (this.destroyed) return;
    this.errorMessage = message;
    this._cdr.detectChanges();
  }

  dismissError(): void {
    if (this.destroyed) return;
    this.errorMessage = '';
    this._cdr.detectChanges();
  }

  private clearErrorMessage(): void {
    if (this.destroyed) return;
    this.errorMessage = '';
    this._cdr.detectChanges();
  }

  private storageKey(username: string): string {
    return this.attemptStoragePrefix + encodeURIComponent(username.trim().toLowerCase());
  }

  private persistAttempt(username: string): boolean {
    if (!this.orderRequest || !this.checkoutFingerprint) {
      return false;
    }
    try {
      const attempt: CheckoutAttempt = {
        username,
        fingerprint: this.checkoutFingerprint,
        orderRequest: this.orderRequest,
        purchasedCartLines: this.purchasedCartLines,
        orderIdempotencyKey: this.orderIdempotencyKey,
        paymentIdempotencyKey: this.paymentIdempotencyKey,
        currentOrder: this.currentOrder,
        paymentId: this.paymentId,
      };
      localStorage.setItem(this.storageKey(username), JSON.stringify(attempt));
      return true;
    } catch (error) {
      reportError('checkout-storage', error);
      return false;
    }
  }

  private restoreAttempt(username: string): void {
    this.resetAttempt();
    this.attemptStorageFailed = false;
    try {
      const raw = localStorage.getItem(this.storageKey(username))
        ?? localStorage.getItem('natiart-checkout-attempt');
      if (!raw) {
        return;
      }
      const attempt = JSON.parse(raw) as Partial<CheckoutAttempt>;
      if (attempt.username !== username) {
        return;
      }
      const request = attempt.orderRequest
        ?? (attempt.currentOrder?.id ? this.replayRequestFromOrder(attempt.currentOrder) : null);
      if (!request || !attempt.orderIdempotencyKey || !attempt.paymentIdempotencyKey) {
        throw new Error('Saved checkout cannot be safely resumed');
      }
      this.orderRequest = request;
      this.purchasedCartLines = Array.isArray(attempt.purchasedCartLines)
        && attempt.purchasedCartLines.every((line) => !!line && typeof line.cartItemId === 'string'
          && line.cartItemId.trim().length > 0 && Number.isSafeInteger(line.quantity) && line.quantity > 0)
        ? attempt.purchasedCartLines : [];
      this.checkoutFingerprint = JSON.stringify(request);
      this.orderIdempotencyKey = attempt.orderIdempotencyKey;
      this.paymentIdempotencyKey = attempt.paymentIdempotencyKey;
      this.currentOrder = attempt.currentOrder ?? null;
      this.paymentId = attempt.paymentId ?? null;
      this.currentStep = 3;
      this.checkoutForm.patchValue({
        userInfo: {
          firstname: request.firstname,
          lastname: request.lastname,
          email: request.email,
          phone: request.phone,
        },
        shippingInfo: {
          country: request.country,
          state: request.state,
          city: request.city,
          neighborhood: request.neighborhood,
          zipCode: request.zipCode,
          street: request.street,
          complement: request.complement,
        },
        paymentInfo: {paymentMethod: PaymentMethod.PIX},
      });
      this.setInfoMessage('A saved PIX checkout is ready to resume. Its order details are fixed.');
      if (!this.persistAttempt(username)) {
        throw new Error('Saved checkout could not be migrated');
      }
      localStorage.removeItem('natiart-checkout-attempt');
    } catch (error) {
      this.resetAttempt();
      this.attemptStorageFailed = true;
      this.clearInfoMessage();
      reportError('checkout-storage', error);
      this.setErrorMessage('Your saved checkout needs assistance before another order can be started.');
    }
  }

  private replayRequestFromOrder(order: OrderDto): OrderDto {
    return {
      firstname: order.firstname,
      lastname: order.lastname,
      email: order.email,
      phone: order.phone,
      country: order.country,
      state: order.state,
      city: order.city,
      neighborhood: order.neighborhood,
      zipCode: order.zipCode,
      street: order.street,
      complement: order.complement,
      items: order.items.map(item => ({productId: item.productId, quantity: item.quantity})),
      deliveryAmount: order.deliveryAmount,
    };
  }

  private clearPersistedAttempt(username: string): boolean {
    try {
      localStorage.removeItem(this.storageKey(username));
      const raw = localStorage.getItem('natiart-checkout-attempt');
      if (!raw || (JSON.parse(raw) as {username?: string}).username === username) {
        localStorage.removeItem('natiart-checkout-attempt');
      }
      return true;
    } catch (error) {
      reportError('checkout-storage', error);
      this.attemptStorageFailed = true;
      this.setErrorMessage('Your saved checkout needs assistance before another order can be started.');
      return false;
    }
  }

  private resetAttempt(): void {
    this.purchasedCartLines = [];
    this.currentOrder = null;
    this.orderRequest = null;
    this.paymentId = null;
    this.checkoutFingerprint = null;
    this.orderIdempotencyKey = crypto.randomUUID();
    this.paymentIdempotencyKey = crypto.randomUUID();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.destroy$.next();
    this.destroy$.complete();
  }
}
