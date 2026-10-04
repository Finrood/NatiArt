import { TestBed, ComponentFixture } from '@angular/core/testing';
import {HttpErrorResponse} from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Router } from '@angular/router';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';

import { CheckoutComponent } from './checkout.component';
import { CartService } from '../../../service/cart.service';
import { OrderService } from '../../../service/order.service';
import { PaymentService } from '../../../service/payment.service';
import { AuthenticationService } from '../../../../directory/service/authentication.service';
import { User, RoleName } from '../../../../directory/models/user.model';
import { OrderDto } from '../../../models/order.model';
import { ShippingQuote, ShippingService } from '../../../service/shipping.service';
import { ProductService } from '../../../service/product.service';

describe('CheckoutComponent', () => {
  let fixture: ComponentFixture<CheckoutComponent>;
  let component: CheckoutComponent;
  let routerNavigateSpy: jasmine.Spy;
  let createPixPaymentSpy: jasmine.Spy;
  let createOrderSpy: jasmine.Spy;
  let isLoggedInSubject: BehaviorSubject<boolean>;
  let currentUserSubject: BehaviorSubject<User | null>;
  let createdOrder: OrderDto;
  const attemptKey = 'natiart-checkout-attempt:user%40example.test';
  let cartItemsSnapshot: Array<{
    cartItemId: string;
    product: { id: string };
    quantity: number;
    goldBorder?: boolean;
    image?: File;
    customImageUploadId?: string;
    requiresArtworkReselection?: boolean;
  }>;
  let uploadCustomerImageSpy: jasmine.Spy;
  let setCustomImageUploadIdSpy: jasmine.Spy;

  it('requires a house number or N/A and bounds it to the persisted size', () => {
    const houseNumber = component.checkoutForm.get('shippingInfo.houseNumber')!;
    houseNumber.setValue('');
    expect(houseNumber.hasError('required')).toBeTrue();
    houseNumber.setValue('   ');
    expect(houseNumber.hasError('pattern')).toBeTrue();
    houseNumber.setValue('N/A');
    expect(houseNumber.valid).toBeTrue();
    houseNumber.setValue('a'.repeat(255));
    expect(houseNumber.valid).toBeTrue();
    houseNumber.setValue('a'.repeat(256));
    expect(houseNumber.hasError('maxlength')).toBeTrue();
  });

  const loggedInUser: User = {
    id: 'u1',
    username: 'user@example.test',
    profile: {
      firstname: 'Ada',
      lastname: 'Lovelace',
      cpf: '52998224725',
      phone: '11999999999',
      country: 'Brazil',
      state: 'SP',
      city: 'Sao Paulo',
      neighborhood: 'Centro',
      zipCode: '01001000',
      street: 'Praca da Se',
    },
    role: RoleName.USER,
    externalId: 'cus_1',
  };

  function paymentResponseWith(paymentId: string | undefined): {
    paymentId: string | undefined;
    creationDate: Date;
    customerId: string;
    billingType: string;
    status: string;
    dueDate: Date;
    invoiceUrl: string;
    invoiceNumber: string;
  } {
    return {
      paymentId,
      creationDate: new Date('2030-01-01T00:00:00Z'),
      customerId: 'cus_1',
      billingType: 'PIX',
      status: 'PENDING',
      dueDate: new Date('2030-01-02T00:00:00Z'),
      invoiceUrl: 'https://example.test/invoice',
      invoiceNumber: '001',
    };
  }

  beforeEach(async () => {
    localStorage.removeItem(attemptKey);
    localStorage.removeItem('natiart-checkout-attempt');
    isLoggedInSubject = new BehaviorSubject<boolean>(true);
    currentUserSubject = new BehaviorSubject<User | null>(loggedInUser);
    createPixPaymentSpy = jasmine.createSpy('createPixPayment');
    createOrderSpy = jasmine.createSpy('createOrder');
    uploadCustomerImageSpy = jasmine.createSpy('uploadCustomerImage');
    setCustomImageUploadIdSpy = jasmine.createSpy('setCustomImageUploadId').and.returnValue(of(undefined));
    cartItemsSnapshot = [{cartItemId: 'line-1', product: {id: 'prod-1'}, quantity: 1}];

    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: CartService,
          useValue: {
            getCartItems: (): BehaviorSubject<never[]> => new BehaviorSubject<never[]>([]),
            getCartTotal: (): BehaviorSubject<number> => new BehaviorSubject<number>(0),
            getCartTotalSnapshot: (): number => 99.9,
            getCartItemsSnapshot: () => cartItemsSnapshot,
            setCustomImageUploadId: setCustomImageUploadIdSpy,
            invalidateArtwork: (id: string): void => {
              for (const item of cartItemsSnapshot) if (item.customImageUploadId === id) {
                delete item.customImageUploadId;
                item.requiresArtworkReselection = !item.image;
              }
            },
            rememberPurchase: jasmine.createSpy('rememberPurchase'),
          },
        },
        {
          provide: OrderService,
          useValue: {
            orderProcessing$: new BehaviorSubject<boolean>(false).asObservable(),
            createOrder: createOrderSpy,
          },
        },
        {
          provide: AuthenticationService,
          useValue: {
            isLoggedIn$: isLoggedInSubject.asObservable(),
            currentUser$: currentUserSubject.asObservable(),
            fetchCurrentUser: (): BehaviorSubject<User | null> => currentUserSubject,
            setAuthTokensAndUser: (): BehaviorSubject<User | null> => currentUserSubject,
          },
        },
        {
          provide: PaymentService,
          useValue: {
            createPixPayment: createPixPaymentSpy,
            getPaymentStatus: jasmine.createSpy('getPaymentStatus').and.returnValue(of({paymentId: 'pay_123', status: 'PENDING'})),
          },
        },
        {
          provide: ProductService,
          useValue: { imageInvalidations: of(), getImage: (): unknown => of(new Blob()), uploadCustomerImage: uploadCustomerImageSpy },
        },
      ],
    }).compileComponents();

    const router: Router = TestBed.inject(Router);
    routerNavigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(CheckoutComponent);
    component = fixture.componentInstance;
    component.checkoutForm.get('shippingInfo.houseNumber')?.setValue('10');
    const quote: ShippingQuote = {
      quoteId: 'quote-1',
      destinationPostalCode: '01001000',
      serviceId: 'correios-pac',
      serviceName: 'PAC',
      expiresAt: '2099-01-01T00:00:00Z',
      itemAmount: 99.9,
      shippingAmount: 7.5,
      totalAmount: 107.4,
      items: [{productId: 'prod-1', quantity: 1, unitPrice: 99.9, lineAmount: 99.9}],
    };
    component.shippingQuote = quote;
    (component as unknown as {shippingQuoteFingerprint: string}).shippingQuoteFingerprint = JSON.stringify({
      zipCode: '01001000',
      items: [{productId: 'prod-1', quantity: 1}],
    });
    createdOrder = {
      id: 'order-123',
      firstname: 'Ada',
      lastname: 'Lovelace',
      email: 'user@example.test',
      country: 'Brazil',
      state: 'SP',
      city: 'Sao Paulo',
      neighborhood: 'Centro',
      zipCode: '01001000',
      street: 'Praca da Se',
      houseNumber: '10',
      items: [],
      deliveryAmount: 7.5,
      totalAmount: 107.4,
    };
    createOrderSpy.and.returnValue(of(createdOrder));
    createPixPaymentSpy.and.returnValue(of(paymentResponseWith('pay_123')));
    fixture.detectChanges();
    component.checkoutForm.get('billingInfo.zipCode')?.setValue('01001-000');
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('preserves a buyer-edited address and house number across user refreshes', () => {
    const shipping = component.checkoutForm.get('shippingInfo')!;
    shipping.get('street')!.setValue('Rua Escolhida');
    shipping.get('houseNumber')!.setValue('42');
    shipping.markAsDirty();

    currentUserSubject.next({
      ...loggedInUser,
      profile: {...loggedInUser.profile!, street: 'Rua do Perfil', city: 'Outra Cidade'}
    });

    expect(shipping.get('street')!.value).toBe('Rua Escolhida');
    expect(shipping.get('houseNumber')!.value).toBe('42');
    expect(shipping.get('city')!.value).toBe('Sao Paulo');
  });

  it('does not overwrite an address typed before a delayed profile arrives', () => {
    fixture.destroy();
    currentUserSubject.next(null);
    const delayedFixture = TestBed.createComponent(CheckoutComponent);
    delayedFixture.detectChanges();
    const shipping = delayedFixture.componentInstance.checkoutForm.get('shippingInfo')!;
    shipping.get('street')!.setValue('Rua Manual');
    shipping.get('houseNumber')!.setValue('15');
    shipping.markAsDirty();

    currentUserSubject.next(loggedInUser);

    expect(shipping.get('street')!.value).toBe('Rua Manual');
    expect(shipping.get('houseNumber')!.value).toBe('15');
    expect(shipping.get('country')!.value).toBe('Brazil');
    delayedFixture.destroy();
    });

  it('fetches an authoritative quote before entering payment', async () => {
    const quoteService = TestBed.inject(ShippingService);
    const quote: ShippingQuote = {
      quoteId: 'fresh-quote',
      destinationPostalCode: '01001000',
      serviceId: 'correios-pac',
      serviceName: 'PAC',
      expiresAt: '2099-01-01T00:00:00Z',
      itemAmount: 99.9,
      shippingAmount: 7.5,
      totalAmount: 107.4,
      items: [{productId: 'prod-1', quantity: 1, unitPrice: 99.9, lineAmount: 99.9}],
    };
    spyOn(quoteService, 'createQuote').and.returnValue(of(quote));
    component.shippingQuote = null;
    (component as unknown as {shippingQuoteFingerprint: string | null}).shippingQuoteFingerprint = null;
    component.currentStep = 2;

    await component.nextStep();

    expect(quoteService.createQuote).toHaveBeenCalledWith({
      zipCode: '01001000',
      items: [{productId: 'prod-1', quantity: 1}],
    });
    expect(component.shippingQuote as unknown as ShippingQuote).toEqual(quote);
    expect(component.currentStep).toBe(3);
  });

  it('quotes two personalized variants and uses owned artwork ids in the order', async () => {
    const artwork = new File(['art'], 'art.png', {type: 'image/png'});
    const uploadId = '2b7f4d7e-6e55-4a8f-a8b2-f2b7069e4d2c';
    cartItemsSnapshot = [
      {cartItemId: 'line-gold', product: {id: 'prod-1'}, quantity: 1, goldBorder: true},
      {cartItemId: 'line-art', product: {id: 'prod-1'}, quantity: 2, image: artwork},
    ];
    uploadCustomerImageSpy.and.returnValue(of({uploadId}));
    const quoteService = TestBed.inject(ShippingService);
    spyOn(quoteService, 'createQuote').and.returnValue(of({
      quoteId: 'variant-quote', destinationPostalCode: '01001000', serviceId: 'pac', serviceName: 'PAC',
      expiresAt: '2099-01-01T00:00:00Z', itemAmount: 37.5, shippingAmount: 8, totalAmount: 45.5,
      items: [
        {productId: 'prod-1', personalizationKey: 'GOLDEN_BORDER=true', quantity: 1, unitPrice: 12.5, lineAmount: 12.5},
        {productId: 'prod-1', personalizationKey: `CUSTOM_IMAGE=${uploadId}`, quantity: 2, unitPrice: 12.5, lineAmount: 25},
      ],
    }));
    component.shippingQuote = null;
    component.currentStep = 2;

    await component.nextStep();

    expect(quoteService.createQuote).toHaveBeenCalledWith({
      zipCode: '01001000',
      items: [
        {productId: 'prod-1', quantity: 1, personalization: {personalizationOptions: {GOLDEN_BORDER: 'true'}}},
        {productId: 'prod-1', quantity: 2, personalization: {personalizationOptions: {CUSTOM_IMAGE: uploadId}}},
      ],
    });
    expect(component.currentStep).toBe(3);
    const orderRequest = await (component as unknown as {buildOrderRequest: () => Promise<OrderDto>}).buildOrderRequest();
    expect(orderRequest.items).toEqual([
      jasmine.objectContaining({productId: 'prod-1', quantity: 1, personalization: {personalizationOptions: {GOLDEN_BORDER: 'true'}}}),
      jasmine.objectContaining({productId: 'prod-1', quantity: 2, personalization: {personalizationOptions: {CUSTOM_IMAGE: uploadId}}}),
    ]);
    expect(orderRequest.shippingQuoteId).toBe('variant-quote');
    expect(uploadCustomerImageSpy).toHaveBeenCalledOnceWith(artwork);
    expect(setCustomImageUploadIdSpy).toHaveBeenCalledOnceWith('line-art', uploadId);
  });

  it('invalidates a definitively rejected artwork ID and uploads the retained File once on retry', async () => {
    const file: File = new File(['art'], 'art.png', {type: 'image/png'});
    cartItemsSnapshot = [{cartItemId: 'line-art', product: {id: 'prod-1'}, quantity: 1,
      image: file, customImageUploadId: 'expired-id'}];
    createOrderSpy.and.returnValue(throwError(() => new HttpErrorResponse({status: 400,
      error: {code: 'CUSTOM_ARTWORK_UNAVAILABLE', uploadId: 'expired-id', orderCreated: false}})));
    (component as unknown as {shippingQuoteFingerprint: string}).shippingQuoteFingerprint =
      (component as unknown as {currentShippingQuoteFingerprint(): string}).currentShippingQuoteFingerprint();
    await component.onProcessPixPayment(loggedInUser);
    const oldKey: string = createOrderSpy.calls.first().args[1];
    expect(cartItemsSnapshot[0].customImageUploadId).toBeUndefined();
    expect(uploadCustomerImageSpy).not.toHaveBeenCalled();
    expect(createPixPaymentSpy).not.toHaveBeenCalled();
    uploadCustomerImageSpy.and.returnValue(of({uploadId: 'fresh-id'}));
    createOrderSpy.and.returnValue(of({id: 'fresh-order', totalAmount: 10}));
    spyOn(TestBed.inject(ShippingService), 'createQuote').and.returnValue(of({quoteId: 'fresh-quote', expiresAt: '2099-01-01T00:00:00Z'} as ShippingQuote));
    await (component as unknown as {loadShippingQuote(): Promise<boolean>}).loadShippingQuote();
    await component.onProcessPixPayment(loggedInUser);
    expect(uploadCustomerImageSpy).toHaveBeenCalledOnceWith(file);
    expect(createOrderSpy.calls.mostRecent().args[0].items[0].personalization.personalizationOptions.CUSTOM_IMAGE).toBe('fresh-id');
    expect(createOrderSpy.calls.mostRecent().args[1]).not.toBe(oldKey);
  });

  it('retains the artwork ID and same order key after an ambiguous response', async () => {
    cartItemsSnapshot = [{cartItemId: 'line-art', product: {id: 'prod-1'}, quantity: 1, customImageUploadId: 'owned-id'}];
    createOrderSpy.and.returnValue(throwError(() => new HttpErrorResponse({status: 0})));
    (component as unknown as {shippingQuoteFingerprint: string}).shippingQuoteFingerprint =
      (component as unknown as {currentShippingQuoteFingerprint(): string}).currentShippingQuoteFingerprint();
    await component.onProcessPixPayment(loggedInUser);
    await component.onProcessPixPayment(loggedInUser);
    expect(cartItemsSnapshot[0].customImageUploadId).toBe('owned-id');
    expect(createOrderSpy.calls.argsFor(0)[1]).toBe(createOrderSpy.calls.argsFor(1)[1]);
    expect(uploadCustomerImageSpy).not.toHaveBeenCalled();
  });


  it('allows a corrected cart and new identity after a typed stock rejection, including reload', async () => {
    cartItemsSnapshot[0].quantity = 2;
    (component as unknown as {shippingQuoteFingerprint: string}).shippingQuoteFingerprint =
      (component as unknown as {currentShippingQuoteFingerprint(): string}).currentShippingQuoteFingerprint();
    createOrderSpy.and.returnValue(throwError(() => new HttpErrorResponse({status: 400,
      error: {code: 'ORDER_CREATION_REJECTED', orderCreated: false}})));
    await component.onProcessPixPayment(loggedInUser);
    const rejectedKey: string = createOrderSpy.calls.first().args[1];
    expect(localStorage.getItem(attemptKey)).toBeNull();
    expect(component.hasSavedAttempt).toBeFalse();
    cartItemsSnapshot[0].quantity = 1;
    fixture.destroy();
    fixture = TestBed.createComponent(CheckoutComponent); component = fixture.componentInstance;
    fixture.detectChanges();
    spyOn(TestBed.inject(ShippingService), 'createQuote').and.returnValue(of({quoteId: 'corrected',
      expiresAt: '2099-01-01T00:00:00Z', items: [], itemAmount: 10, shippingAmount: 0, totalAmount: 10} as unknown as ShippingQuote));
    await (component as unknown as {loadShippingQuote(): Promise<boolean>}).loadShippingQuote();
    createOrderSpy.and.returnValue(of(createdOrder));
    await component.onProcessPixPayment(loggedInUser);
    expect(createOrderSpy.calls.mostRecent().args[0].items[0].quantity).toBe(1);
    expect(createOrderSpy.calls.mostRecent().args[1]).not.toBe(rejectedKey);
  });

  it('keeps immutable details for untyped 400 and server failures', async () => {
    for (const status of [400, 500]) {
      createOrderSpy.and.returnValue(throwError(() => new HttpErrorResponse({status})));
      await component.onProcessPixPayment(loggedInUser);
      cartItemsSnapshot[0].quantity = 2;
      await component.onProcessPixPayment(loggedInUser);
      expect(createOrderSpy.calls.mostRecent().args[0].items[0].quantity).toBe(1);
      expect(createOrderSpy.calls.mostRecent().args[1]).toBe(createOrderSpy.calls.first().args[1]);
    }
  });

  it('keeps an accepted attempt when payment rejects, even with a precreation marker', async () => {
    createPixPaymentSpy.and.returnValue(throwError(() => new HttpErrorResponse({status: 400,
      error: {code: 'ORDER_CREATION_REJECTED', orderCreated: false}})));
    await component.onProcessPixPayment(loggedInUser);
    const stored: string | null = localStorage.getItem(attemptKey);
    expect(stored).not.toBeNull();
    expect(JSON.parse(stored!).currentOrder.id).toBe(createdOrder.id);
    expect(component.hasSavedAttempt).toBeTrue();
  });

  it('keeps checkout errors visible until dismissed (O3)', async () => {
    await component.onSubmit();

    expect(component.errorMessage).toContain('Please correct the errors');

    component.dismissError();
    expect(component.errorMessage).toBe('');
  });

  it('routes status messages to infoMessage, never errorMessage (O3)', () => {
    const internals = component as unknown as {
      setInfoMessage(message: string): void;
      clearInfoMessage(): void;
    };

    internals.setInfoMessage('Creating a temporary account...');
    expect(component.infoMessage).toContain('temporary account');
    expect(component.errorMessage).toBe('');

    internals.clearInfoMessage();
    expect(component.infoMessage).toBe('');
  });

  it('ignores a second submit while one is in flight (AH1)', async () => {
    component.checkoutForm.get('userInfo')?.setValue({
      firstname: 'Ada',
      lastname: 'Lovelace',
      cpf: '529.982.247-25',
      email: 'guest@example.test',
      phone: '(11) 99999-9999',
    });
    component.checkoutForm.get('shippingInfo')?.setValue({
      country: 'Brazil',
      state: 'SP',
      city: 'Sao Paulo',
      neighborhood: 'Centro',
      zipCode: '01001-000',
      street: 'Praca da Se',
      houseNumber: '10',
      complement: '',
    });
    component.checkoutForm.get('paymentInfo.paymentMethod')?.setValue('PIX');
    component.checkoutForm.get('billingInfo')?.patchValue({zipCode: '01001-000'});
    component.shippingQuote = {
      quoteId: 'quote-1',
      destinationPostalCode: '01001000',
      serviceId: 'correios-pac',
      serviceName: 'PAC',
      expiresAt: '2099-01-01T00:00:00Z',
      itemAmount: 99.9,
      shippingAmount: 7.5,
      totalAmount: 107.4,
      items: [{productId: 'prod-1', quantity: 1, unitPrice: 99.9, lineAmount: 99.9}],
    };
    (component as unknown as {shippingQuoteFingerprint: string}).shippingQuoteFingerprint = JSON.stringify({
      zipCode: '01001000',
      items: [{productId: 'prod-1', quantity: 1}],
    });
    component.checkoutForm.get('billingInfo.zipCode')?.setValue('01001-000');
    expect(component.checkoutForm.invalid).toBeFalse();

    const first: Promise<void> = component.onSubmit();
    expect(component.isSubmitting).toBeTrue();
    await component.onSubmit();
    await first;

    expect(createPixPaymentSpy).toHaveBeenCalledTimes(1);
    expect(component.isSubmitting).toBeFalse();
    expect(routerNavigateSpy).toHaveBeenCalledWith(['/pix-payment', 'pay_123']);
  });

  it('resets isSubmitting and surfaces the error after a failed PIX payment (AH1)', async () => {
    createPixPaymentSpy.and.returnValue(throwError(() => new Error('upstream down')));

    await component.onProcessPixPayment(loggedInUser);

    expect(routerNavigateSpy).not.toHaveBeenCalled();
    expect(component.errorMessage).toContain('attempt has been kept');
    expect(component.isSubmitting).toBeFalse();
  });

  it('reuses the created order and payment key when PIX payment is retried', async () => {
    createPixPaymentSpy.and.returnValue(throwError(() => new Error('upstream down')));
    await component.onProcessPixPayment(loggedInUser);

    createPixPaymentSpy.and.returnValue(of(paymentResponseWith('pay_123')));
    await component.onProcessPixPayment(loggedInUser);

    expect(createOrderSpy).toHaveBeenCalledTimes(2);
    expect(createOrderSpy.calls.argsFor(1)[1]).toBe(createOrderSpy.calls.argsFor(0)[1]);
    expect(createPixPaymentSpy).toHaveBeenCalledTimes(2);
    expect(createPixPaymentSpy.calls.argsFor(0)[1]).toMatch(/^[0-9a-f-]{36}$/);
    expect(createPixPaymentSpy.calls.argsFor(1)[1]).toBe(createPixPaymentSpy.calls.argsFor(0)[1]);
    expect(createPixPaymentSpy.calls.argsFor(0)[0]).toEqual(jasmine.objectContaining({orderId: 'order-123', value: 107.4}));
  });

  it('navigates to the PIX confirmation when the payment response carries an id', async () => {
    await component.onProcessPixPayment(loggedInUser);

    expect(createPixPaymentSpy).toHaveBeenCalledTimes(1);
    expect(routerNavigateSpy).toHaveBeenCalledWith(['/pix-payment', 'pay_123']);
  });

  it('never navigates to /pix-payment/undefined when the payment id is missing', async () => {
    createPixPaymentSpy.and.returnValue(of(paymentResponseWith(undefined)));

    await component.onProcessPixPayment(loggedInUser);

    expect(routerNavigateSpy).not.toHaveBeenCalled();
    expect(component.errorMessage).toContain('Could not process PIX payment');
  });

  it('blocks unauthenticated checkout before payment', async () => {
    isLoggedInSubject.next(false);
    currentUserSubject.next(loggedInUser);
    component.checkoutForm.get('userInfo')?.setValue({
      firstname: 'Ada',
      lastname: 'Lovelace',
      cpf: '529.982.247-25',
      email: 'guest@example.test',
      phone: '(11) 99999-9999',
    });
    component.checkoutForm.get('shippingInfo')?.setValue({
      country: 'Brazil',
      state: 'SP',
      city: 'Sao Paulo',
      neighborhood: 'Centro',
      zipCode: '01001-000',
      street: 'Praca da Se',
      houseNumber: '10',
      complement: '',
    });
    component.checkoutForm.get('paymentInfo.paymentMethod')?.setValue('PIX');
    component.checkoutForm.get('billingInfo.zipCode')?.setValue('01001-000');
    expect(component.checkoutForm.invalid).toBeFalse();

    await component.onSubmit();

    expect(component.errorMessage).toContain('Please sign in or register');
    expect(createPixPaymentSpy).not.toHaveBeenCalled();
    expect(routerNavigateSpy).not.toHaveBeenCalled();
  });

  it('replays the same order key after a lost creation response and reload', async () => {
    createOrderSpy.and.returnValue(throwError(() => new Error('response lost')));
    await component.onProcessPixPayment(loggedInUser);

    const saved = JSON.parse(localStorage.getItem(attemptKey) ?? '{}') as {
      currentOrder: OrderDto | null;
      orderRequest: OrderDto;
      orderIdempotencyKey: string;
    };
    expect(saved.currentOrder).toBeNull();
    expect(JSON.parse(localStorage.getItem(attemptKey)!).purchasedCartLines)
      .toEqual([{cartItemId: 'line-1', quantity: 1}]);
    expect(saved.orderRequest.items).toEqual([{productId: 'prod-1', quantity: 1}]);
    expect(createPixPaymentSpy).not.toHaveBeenCalled();

    fixture.destroy();
    createOrderSpy.and.returnValue(of(createdOrder));
    fixture = TestBed.createComponent(CheckoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Resume saved checkout');
    const resumeButton = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(resumeButton.disabled).toBeFalse();
    resumeButton.click();
    await fixture.whenStable();

    expect(createOrderSpy).toHaveBeenCalledTimes(2);
    expect(createOrderSpy.calls.argsFor(1)[1]).toBe(saved.orderIdempotencyKey);
    expect(createOrderSpy.calls.argsFor(1)[0]).toEqual(saved.orderRequest);
    expect(createPixPaymentSpy).toHaveBeenCalledTimes(1);
    expect(routerNavigateSpy).toHaveBeenCalledWith(['/pix-payment', 'pay_123']);
    expect(localStorage.getItem(attemptKey)).not.toBeNull();
    expect(JSON.parse(localStorage.getItem(attemptKey)!).purchasedCartLines)
      .toEqual([{cartItemId: 'line-1', quantity: 1}]);
  });

  it('replays the same payment key after provider acceptance with a lost response', async () => {
    createPixPaymentSpy.and.returnValue(throwError(() => new Error('response lost')));
    await component.onProcessPixPayment(loggedInUser);
    const saved = JSON.parse(localStorage.getItem(attemptKey) ?? '{}') as {
      paymentIdempotencyKey: string;
      currentOrder: OrderDto;
    };
    expect(saved.currentOrder.id).toBe('order-123');
    expect(routerNavigateSpy).not.toHaveBeenCalled();

    fixture.destroy();
    createPixPaymentSpy.and.returnValue(of(paymentResponseWith('pay_123')));
    fixture = TestBed.createComponent(CheckoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await component.onSubmit();

    expect(createPixPaymentSpy).toHaveBeenCalledTimes(2);
    expect(createPixPaymentSpy.calls.argsFor(1)[1]).toBe(saved.paymentIdempotencyKey);
    expect(routerNavigateSpy).toHaveBeenCalledWith(['/pix-payment', 'pay_123']);
    expect(JSON.parse(localStorage.getItem(attemptKey) ?? '{}').paymentId).toBe('pay_123');
  });

  it('retains payment identity when routing fails and checks its status before resuming', async () => {
    routerNavigateSpy.and.resolveTo(false);
    await component.onProcessPixPayment(loggedInUser);
    expect(JSON.parse(localStorage.getItem(attemptKey) ?? '{}').paymentId).toBe('pay_123');

    routerNavigateSpy.and.resolveTo(true);
    await component.onProcessPixPayment(loggedInUser);

    const paymentService = TestBed.inject(PaymentService);
    expect(paymentService.getPaymentStatus).toHaveBeenCalledWith('pay_123');
    expect(createPixPaymentSpy).toHaveBeenCalledTimes(1);
    expect(routerNavigateSpy).toHaveBeenCalledTimes(2);
    expect(localStorage.getItem(attemptKey)).not.toBeNull();
  });

  it('keeps the saved key and starts no payment after leaving during a delayed order response', async () => {
    const delayedOrder = new Subject<OrderDto>();
    let sent: () => void = (): void => {};
    const requestSent: Promise<void> = new Promise<void>(resolve => {sent = resolve;});
    createOrderSpy.and.callFake(() => {sent(); return delayedOrder;});
    const inFlight = component.onProcessPixPayment(loggedInUser);
    await requestSent;
    expect(createOrderSpy).toHaveBeenCalled();
    expect(localStorage.getItem(attemptKey)).not.toBeNull();

    fixture.destroy();
    delayedOrder.next(createdOrder);
    delayedOrder.complete();
    await inFlight;

    expect(createPixPaymentSpy).not.toHaveBeenCalled();
    expect(routerNavigateSpy).not.toHaveBeenCalled();
    expect(localStorage.getItem(attemptKey)).not.toBeNull();
  });

  it('clears the saved attempt only after the payment is confirmed complete', async () => {
    await component.onProcessPixPayment(loggedInUser);
    expect(localStorage.getItem(attemptKey)).not.toBeNull();
    const paymentService = TestBed.inject(PaymentService);
    (paymentService.getPaymentStatus as jasmine.Spy).and.returnValue(of({paymentId: 'pay_123', status: 'COMPLETED'}));

    await component.onProcessPixPayment(loggedInUser);

    expect(localStorage.getItem(attemptKey)).toBeNull();
    expect(createPixPaymentSpy).toHaveBeenCalledTimes(1);
    expect(component.infoMessage).toContain('already completed');
  });

  it('keeps one account’s saved attempt separate when another account signs in', async () => {
    await component.onProcessPixPayment(loggedInUser);
    expect(localStorage.getItem(attemptKey)).not.toBeNull();

    fixture.destroy();
    currentUserSubject.next({...loggedInUser, id: 'u2', username: 'other@example.test'});
    fixture = TestBed.createComponent(CheckoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.hasSavedAttempt).toBeFalse();
    expect(localStorage.getItem(attemptKey)).not.toBeNull();
  });

  it('does not create an order when the attempt cannot be saved first', async () => {
    spyOn(Storage.prototype, 'setItem').and.throwError('storage unavailable');

    await component.onProcessPixPayment(loggedInUser);

    expect(createOrderSpy).not.toHaveBeenCalled();
    expect(createPixPaymentSpy).not.toHaveBeenCalled();
    expect(component.errorMessage).toContain('Could not save your checkout');
  });
});
