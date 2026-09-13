import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Router } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { CheckoutComponent } from './checkout.component';
import { CartService } from '../../../service/cart.service';
import { ProductService } from '../../../service/product.service';
import { OrderService } from '../../../service/order.service';
import { PaymentService } from '../../../service/payment.service';
import { AuthenticationService } from '../../../../directory/service/authentication.service';
import { User, RoleName } from '../../../../directory/models/user.model';
import { OrderDto } from '../../../models/order.model';

describe('CheckoutComponent', () => {
  let fixture: ComponentFixture<CheckoutComponent>;
  let component: CheckoutComponent;
  let routerNavigateSpy: jasmine.Spy;
  let createPixPaymentSpy: jasmine.Spy;
  let createOrderSpy: jasmine.Spy;
  let isLoggedInSubject: BehaviorSubject<boolean>;
  let currentUserSubject: BehaviorSubject<User | null>;
  let cartItemsSnapshot: Array<{
    cartItemId: string;
    product: { id: string };
    quantity: number;
    goldBorder?: boolean;
    image?: File;
    customImageUploadId?: string;
  }>;
  let uploadCustomerImageSpy: jasmine.Spy;
  let setCustomImageUploadIdSpy: jasmine.Spy;

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
          useValue: { createPixPayment: createPixPaymentSpy },
        },
        {
          provide: ProductService,
          useValue: { uploadCustomerImage: uploadCustomerImageSpy },
        },
      ],
    }).compileComponents();

    const router: Router = TestBed.inject(Router);
    routerNavigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(CheckoutComponent);
    component = fixture.componentInstance;
    const createdOrder: OrderDto = {
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
      items: [],
      deliveryAmount: 7.5,
      totalAmount: 107.4,
    };
    createOrderSpy.and.returnValue(of(createdOrder));
    createPixPaymentSpy.and.returnValue(of(paymentResponseWith('pay_123')));
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('buildsDistinctVariantLinesAndSendsOwnedArtworkIds', async () => {
    const artwork = new File(['art'], 'art.png', {type: 'image/png'});
    cartItemsSnapshot = [
      {cartItemId: 'line-gold', product: {id: 'prod-1'}, quantity: 1, goldBorder: true},
      {cartItemId: 'line-art', product: {id: 'prod-1'}, quantity: 2, image: artwork},
    ];
    uploadCustomerImageSpy.and.returnValue(of({uploadId: '2b7f4d7e-6e55-4a8f-a8b2-f2b7069e4d2c'}));

    const request = await (component as unknown as {buildOrderRequest: () => Promise<OrderDto>}).buildOrderRequest();

    expect(request.items).toEqual([
      jasmine.objectContaining({
        productId: 'prod-1',
        quantity: 1,
        personalization: {personalizationOptions: {GOLDEN_BORDER: 'true'}},
      }),
      jasmine.objectContaining({
        productId: 'prod-1',
        quantity: 2,
        personalization: {personalizationOptions: {CUSTOM_IMAGE: '2b7f4d7e-6e55-4a8f-a8b2-f2b7069e4d2c'}},
      }),
    ]);
    expect(uploadCustomerImageSpy).toHaveBeenCalledOnceWith(artwork);
    expect(setCustomImageUploadIdSpy).toHaveBeenCalledOnceWith(
      'line-art', '2b7f4d7e-6e55-4a8f-a8b2-f2b7069e4d2c');
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
      complement: '',
    });
    component.checkoutForm.get('paymentInfo.paymentMethod')?.setValue('PIX');
    component.checkoutForm.get('billingInfo')?.patchValue({ zipCode: '01001-000' });
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
    expect(component.errorMessage).toContain('Could not process PIX payment');
    expect(component.isSubmitting).toBeFalse();
  });

  it('reuses the created order and payment key when PIX payment is retried', async () => {
    createPixPaymentSpy.and.returnValue(throwError(() => new Error('upstream down')));
    await component.onProcessPixPayment(loggedInUser);

    createPixPaymentSpy.and.returnValue(of(paymentResponseWith('pay_123')));
    await component.onProcessPixPayment(loggedInUser);

    expect(createOrderSpy).toHaveBeenCalledTimes(1);
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
      complement: '',
    });
    component.checkoutForm.get('paymentInfo.paymentMethod')?.setValue('PIX');
    component.checkoutForm.get('billingInfo')?.patchValue({ zipCode: '01001-000' });
    expect(component.checkoutForm.invalid).toBeFalse();

    await component.onSubmit();

    expect(component.errorMessage).toContain('Please sign in or register');
    expect(createPixPaymentSpy).not.toHaveBeenCalled();
    expect(routerNavigateSpy).not.toHaveBeenCalled();
  });
});
