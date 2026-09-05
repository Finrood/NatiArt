import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';

import { CheckoutComponent } from './checkout.component';
import { CartService } from '../../../service/cart.service';
import { OrderService } from '../../../service/order.service';
import { PaymentService } from '../../../service/payment.service';
import { AuthenticationService } from '../../../../directory/service/authentication.service';
import { SignupService } from '../../../../directory/service/signup.service';
import { User, RoleName } from '../../../../directory/models/user.model';

describe('CheckoutComponent', () => {
  let fixture: ComponentFixture<CheckoutComponent>;
  let component: CheckoutComponent;
  let routerNavigateSpy: jasmine.Spy;
  let createPixPaymentSpy: jasmine.Spy;
  let registerGhostUserSpy: jasmine.Spy;
  let isLoggedInSubject: BehaviorSubject<boolean>;
  let currentUserSubject: BehaviorSubject<User | null>;

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
    routerNavigateSpy = jasmine.createSpy('navigate');
    createPixPaymentSpy = jasmine.createSpy('createPixPayment');
    registerGhostUserSpy = jasmine.createSpy('registerGhostUser');

    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideAnimations(),
        { provide: Router, useValue: { navigate: routerNavigateSpy } },
        {
          provide: CartService,
          useValue: {
            getCartItems: (): BehaviorSubject<never[]> => new BehaviorSubject<never[]>([]),
            getCartTotal: (): BehaviorSubject<number> => new BehaviorSubject<number>(0),
            getCartTotalSnapshot: (): number => 99.9,
          },
        },
        {
          provide: OrderService,
          useValue: { orderProcessing$: new BehaviorSubject<boolean>(false).asObservable() },
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
          provide: SignupService,
          useValue: { registerGhostUser: registerGhostUserSpy },
        },
        {
          provide: PaymentService,
          useValue: { createPixPayment: createPixPaymentSpy },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CheckoutComponent);
    component = fixture.componentInstance;
    createPixPaymentSpy.and.returnValue(of(paymentResponseWith('pay_123')));
    registerGhostUserSpy.and.returnValue(of({ accessToken: 'a', refreshToken: 'r' }));
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
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

  it('resolves the guest user once for a guest PIX submit', async () => {
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
    expect(component.checkoutForm.invalid).toBeFalse();

    await component.onSubmit();

    expect(registerGhostUserSpy).toHaveBeenCalledTimes(1);
    expect(createPixPaymentSpy).toHaveBeenCalledTimes(1);
    expect(routerNavigateSpy).toHaveBeenCalledWith(['/pix-payment', 'pay_123']);
  });
});
