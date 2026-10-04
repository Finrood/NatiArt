import {HttpErrorResponse} from '@angular/common/http';
import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnDestroy, OnInit} from '@angular/core';
import { AsyncPipe, CommonModule } from '@angular/common';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {EmptyError, firstValueFrom, map, Observable, Subject, throwError} from 'rxjs';
import {CartItem} from '../../../models/CartItem.model';
import {OrderDto} from '../../../models/order.model';
import {OrderItemDto} from '../../../models/orderItem.model';
import {CartService, PurchasedCartLine} from '../../../service/cart.service';
import {ProductService} from '../../../service/product.service';
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
import {ShippingQuote, ShippingQuoteRequest, ShippingService} from '../../../service/shipping.service';
import {PersonalizationOption} from '../../../models/support/personalization-option';

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
  readonly calculatingShipping: string = $localize`Calculating shipping...`;
  readonly nextPayment: string = $localize`Next: Payment`;
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
  shippingQuote: ShippingQuote | null = null;
  isLoadingQuote = false;

  private shippingQuoteFingerprint: string | null = null;
  private readonly _productService = inject(ProductService);
  private readonly _shippingService = inject(ShippingService);

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
        houseNumber: ['', [Validators.required, Validators.maxLength(255), Validators.pattern(/\S/)]],
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

  async nextStep(): Promise<void> {
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
      if (!(await this.loadShippingQuote())) {
        return;
      }
    }

    if (this.currentStep < 3) {
      this.currentStep++;
      this._cdr.markForCheck();
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
            const userInfo = this.checkoutForm.get('userInfo');
            const shippingInfo = this.checkoutForm.get('shippingInfo');
            if (userInfo?.pristine) {
              userInfo.patchValue({firstname: user.profile.firstname, lastname: user.profile.lastname,
                email: user.username, cpf: this.formatCpf(user.profile.cpf), phone: user.profile.phone});
              if (userInfo.invalid) userInfo.markAllAsTouched();
            }
            if (shippingInfo?.pristine) {
              shippingInfo.patchValue({country: user.profile.country || 'Brazil', state: user.profile.state,
                city: user.profile.city, neighborhood: user.profile.neighborhood, zipCode: user.profile.zipCode,
                street: user.profile.street, complement: user.profile.complement});
              if (shippingInfo.invalid) shippingInfo.markAllAsTouched();
            }
            if (this.sameShippingAsBilling) {
              this.checkoutForm.get('billingInfo')?.patchValue(shippingInfo?.value);
            }
          }
        })
      )
      .subscribe();

    this.checkoutForm.get('shippingInfo')?.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.shippingQuote = null;
        this.shippingQuoteFingerprint = null;
        if (!this.orderRequest) {
          this.currentOrder = null;
          this.checkoutFingerprint = null;
        }
      });

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
          this.setErrorMessage($localize`Please sign in or register before checking out.`);
          return throwError(() => new Error($localize`Guest checkout requires an authenticated account.`));
        } else {
          return this.currentUser$.pipe(map(user => {
            if (!user) throw new Error($localize`No logged-in user found.`);
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
      // Provisioning can complete after login; retrieve the latest caller state.
      const paymentUser: User = user?.externalId
        ? user
        : await firstValueFrom(this._authenticationService.fetchCurrentUser().pipe(takeUntil(this.destroy$)));
      if (this.destroyed) return;
      if (!paymentUser.externalId) {
        if (paymentUser.provisioningStatus === 'PENDING' || paymentUser.provisioningStatus === 'IN_PROGRESS') {
          this.setErrorMessage($localize`Your payment account is being prepared. Please try again shortly.`);
        } else if (paymentUser.provisioningStatus === 'FAILED') {
          this.setErrorMessage($localize`Your payment account needs assistance. Please contact support.`);
        } else {
          this.setErrorMessage($localize`Could not retrieve customer ID for payment. Please try again.`);
        }
        return;
      }

      if (!this.orderRequest) {
      if (!this.shippingQuote || this.isShippingQuoteExpired()) {
        this.setErrorMessage($localize`The shipping total is no longer current. Return to Shipping and review the refreshed quote.`);
        return;
      }
      let currentQuoteFingerprint: string;
      try {
        currentQuoteFingerprint = this.currentShippingQuoteFingerprint();
      } catch {
        this.setErrorMessage($localize`The shipping total is no longer current. Return to Shipping and review the refreshed quote.`);
        return;
      }
      if (this.shippingQuoteFingerprint !== currentQuoteFingerprint) {
        this.setErrorMessage($localize`The shipping total is no longer current. Return to Shipping and review the refreshed quote.`);
        return;
      }
        const orderRequest = await this.buildOrderRequest();
        if (this.destroyed) return;
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
          this.setErrorMessage($localize`Could not save your checkout. Please free browser storage and try again.`);
          return;
        }
      }

      this.setInfoMessage(this.currentOrder ? $localize`Checking your saved order...` : $localize`Creating your order...`);
      const order = await firstValueFrom(this._orderService
        .createOrder(this.orderRequest, this.orderIdempotencyKey)
        .pipe(takeUntil(this.destroy$)));
      if (this.destroyed) {
        return;
      }
      this.clearInfoMessage();
      if (!order?.id || order.totalAmount == null) {
        this.setErrorMessage($localize`Could not confirm your order. Please try again.`);
        return;
      }
      this._cartService.rememberPurchase(order.id, paymentUser.externalId, this.purchasedCartLines);
      this.currentOrder = order;
      this.persistAttempt(user.username);

      if (order.status === 'CANCELLED' || ['PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED'].includes(order.status ?? '')) {
        if (this.clearPersistedAttempt(user.username)) {
          this.resetAttempt();
        }
        this.setInfoMessage(order.status === 'CANCELLED'
          ? $localize`The saved order was cancelled. You can start a new checkout.`
          : $localize`The saved order has already been paid.`);
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
          this.setInfoMessage($localize`Your PIX payment has already completed.`);
          return;
        }
        await this.navigateToPix(this.paymentId);
        return;
      }

      const pixPaymentData: PaymentCreationRequest = {
        paymentProcessor: 'ASAAS',
        customerId: paymentUser.externalId,
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
        this.setErrorMessage($localize`Could not process PIX payment. Please try again.`);
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
      if (this.recoverRejectedOrder(error, user.username)) return;
      reportError('payment', error);
      this.setErrorMessage($localize`Could not confirm your saved checkout. Please retry; your attempt has been kept.`);
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
      this.setErrorMessage($localize`Could not open the payment page. Your checkout is saved; please try again.`);
    }
  }

  private recoverRejectedOrder(error: unknown, username: string): boolean {
    if (this.currentOrder || this.paymentId || !(error instanceof HttpErrorResponse) || error.status !== 400 ||
        error.error?.orderCreated !== false ||
        !['ORDER_CREATION_REJECTED', 'CUSTOM_ARTWORK_UNAVAILABLE'].includes(error.error?.code)) return false;
    if (!this.clearPersistedAttempt(username)) return true;
    if (error.error.code === 'CUSTOM_ARTWORK_UNAVAILABLE' && typeof error.error.uploadId === 'string') {
      this._cartService.invalidateArtwork(error.error.uploadId);
    }
    this.resetAttempt();
    this.shippingQuote = null;
    this.shippingQuoteFingerprint = null;
    this.currentStep = 2;
    this.clearInfoMessage();
    this.setErrorMessage($localize`No order was created. Review your cart and artwork, then confirm shipping again.`);
    return true;
  }

  private recoverArtwork(error: unknown): boolean {
    if (this.orderRequest || !(error instanceof HttpErrorResponse) || error.status !== 400 ||
        error.error?.code !== 'CUSTOM_ARTWORK_UNAVAILABLE' || error.error?.orderCreated !== false ||
        typeof error.error.uploadId !== 'string') return false;
    this._cartService.invalidateArtwork(error.error.uploadId);
    this.shippingQuote = null;
    this.shippingQuoteFingerprint = null;
    this.setErrorMessage($localize`Your artwork is no longer available. Select it again and confirm shipping.`);
    return true;
  }

  reselectArtwork(cartItemId: string, event: Event): void {
    const input: HTMLInputElement = event.target as HTMLInputElement;
    const file: File | undefined = input.files?.[0];
    if (file) this._cartService.reselectArtwork(cartItemId, file);
    input.value = '';
    this._cdr.markForCheck();
  }

  private async buildOrderRequest(): Promise<OrderDto> {
    const userInfo = this.checkoutForm.get('userInfo')?.getRawValue();
    const shippingInfo = this.checkoutForm.get('shippingInfo')?.getRawValue();
    const items = await this.buildOrderItems();

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
      houseNumber: shippingInfo.houseNumber.trim(),
      complement: shippingInfo.complement,
      items,
      shippingQuoteId: this.shippingQuote?.quoteId,
    };
  }

  private async buildOrderItems(): Promise<OrderItemDto[]> {
    const items = await Promise.all(this._cartService.getCartItemsSnapshot().map(async item => {
      if (item.requiresArtworkReselection) throw new Error($localize`Select your artwork again before checkout.`);
      if (item.image && !item.customImageUploadId) {
        this.setInfoMessage($localize`Uploading your custom artwork...`);
        const upload = await firstValueFrom(this._productService.uploadCustomerImage(item.image));
        if (!upload?.uploadId || upload.uploadId.trim().length === 0) {
          throw new Error($localize`The artwork upload did not return an upload identifier.`);
        }
        item.customImageUploadId = upload.uploadId;
        await firstValueFrom(this._cartService.setCustomImageUploadId(item.cartItemId, upload.uploadId));
      }
      return this.orderItemFromCart(item);
    }));
    if (items.length === 0) {
      throw new Error($localize`Cannot create an order from an empty cart.`);
    }
    return items;
  }

  private orderItemFromCart(item: CartItem): OrderItemDto {
      if (!item.product.id) {
        throw new Error($localize`A cart item is missing its product identifier.`);
      }
      if (item.image && !item.customImageUploadId) {
        throw new Error($localize`A custom artwork line is missing its upload identifier.`);
      }
      const personalizationOptions: Partial<Record<PersonalizationOption, string>> = {};
      if (item.goldBorder) {
        personalizationOptions[PersonalizationOption.GOLDEN_BORDER] = 'true';
      }
      if (item.customImageUploadId) {
        personalizationOptions[PersonalizationOption.CUSTOM_IMAGE] = item.customImageUploadId;
      }
      const personalization = Object.keys(personalizationOptions).length > 0
        ? {personalizationOptions}
        : undefined;
      return {productId: item.product.id, quantity: item.quantity, personalization};
  }

  private async loadShippingQuote(): Promise<boolean> {
    this.isLoadingQuote = true;
    try {
      const items = await this.buildOrderItems();
      const request = this.buildShippingQuoteRequest(items);
      const fingerprint = JSON.stringify(request);
      if (this.shippingQuote
        && this.shippingQuoteFingerprint === fingerprint
        && !this.isShippingQuoteExpired()) {
        return true;
      }
      this.setInfoMessage($localize`Calculating the shipping total...`);
      this.shippingQuote = await firstValueFrom(this._shippingService.createQuote(request));
      this.shippingQuoteFingerprint = fingerprint;
      this.clearErrorMessage();
      return true;
    } catch (error) {
      if (this.recoverArtwork(error)) return false;
      reportError('checkout-shipping-quote', error);
      this.shippingQuote = null;
      this.shippingQuoteFingerprint = null;
      this.setErrorMessage($localize`Shipping is unavailable for this address or cart. Please review the address and try again.`);
      return false;
    } finally {
      this.isLoadingQuote = false;
      this.clearInfoMessage();
      this._cdr.detectChanges();
    }
  }

  private buildShippingQuoteRequest(items?: OrderItemDto[]): ShippingQuoteRequest {
    const shippingInfo = this.checkoutForm.get('shippingInfo')?.getRawValue();
    const quoteItems = items ?? this._cartService.getCartItemsSnapshot().map(item => this.orderItemFromCart(item));
    if (quoteItems.length === 0) {
      throw new Error($localize`Cannot quote shipping for an empty cart.`);
    }
    return {
      zipCode: shippingInfo.zipCode.replace(/\D/g, ''),
      items: quoteItems.map(item => item.personalization
        ? {productId: item.productId, quantity: item.quantity, personalization: item.personalization}
        : {productId: item.productId, quantity: item.quantity}),
    };
  }

  private currentShippingQuoteFingerprint(): string {
    return JSON.stringify(this.buildShippingQuoteRequest());
  }

  private isShippingQuoteExpired(): boolean {
    const expiresAt = this.shippingQuote ? Date.parse(this.shippingQuote.expiresAt) : NaN;
    return !Number.isFinite(expiresAt) || expiresAt <= Date.now();
  }

  async onSubmit(): Promise<void> {
    if (this.destroyed) {
      return;
    }
    if (this.attemptStorageFailed) {
      this.setErrorMessage($localize`Your saved checkout needs assistance before another order can be started.`);
      return;
    }
    this.clearErrorMessage();
    if (this.checkoutForm.invalid && !this.hasSavedAttempt) {
      this.checkoutForm.markAllAsTouched();
      this.setErrorMessage($localize`Please correct the errors in the form.`);
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

      this.setErrorMessage($localize`Invalid payment method selected.`);

    } catch (error) {
      if (this.destroyed) {
        return;
      }
      reportError('checkout', error);
      if (!this.errorMessage) {
        this.setErrorMessage($localize`An unexpected error occurred during checkout.`);
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
        throw new Error($localize`Saved checkout cannot be safely resumed`);
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
          street: request.street, houseNumber: request.houseNumber,
          complement: request.complement,
        },
        paymentInfo: {paymentMethod: PaymentMethod.PIX},
      });
      this.setInfoMessage($localize`A saved PIX checkout is ready to resume. Its order details are fixed.`);
      if (!this.persistAttempt(username)) {
        throw new Error($localize`Saved checkout could not be migrated`);
      }
      localStorage.removeItem('natiart-checkout-attempt');
    } catch (error) {
      this.resetAttempt();
      this.attemptStorageFailed = true;
      this.clearInfoMessage();
      reportError('checkout-storage', error);
      this.setErrorMessage($localize`Your saved checkout needs assistance before another order can be started.`);
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
      houseNumber: order.houseNumber,
      complement: order.complement,
      items: order.items.map(item => ({productId: item.productId, quantity: item.quantity, personalization: item.personalization})),
      shippingQuoteId: order.shippingQuoteId,
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
      this.setErrorMessage($localize`Your saved checkout needs assistance before another order can be started.`);
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
