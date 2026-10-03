import {TestBed} from '@angular/core/testing';
import {provideHttpClient, HttpErrorResponse} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, Router} from '@angular/router';
import {of, throwError} from 'rxjs';
import {CheckoutComponent} from './checkout.component';
import {CartService} from '../../../service/cart.service';
import {ProductService} from '../../../service/product.service';
import {OrderService} from '../../../service/order.service';
import {PaymentService} from '../../../service/payment.service';
import {AuthenticationService} from '../../../../directory/service/authentication.service';
import {RoleName, User} from '../../../../directory/models/user.model';
import {ShippingService, ShippingQuote} from '../../../service/shipping.service';
import {Product} from '../../../models/product.model';

describe('Restored artwork reselection', (): void => {
  const user: User = {id: 'u1', username: 'buyer@example.test', externalId: 'owner', role: RoleName.USER,
    profile: {firstname: 'Buyer', lastname: 'Customer', cpf: '52998224725', phone: '11999999999',
      country: 'Brazil', state: 'SP', city: 'City', neighborhood: 'Area', zipCode: '01001000', street: 'Street'}};
  const product: Product = {id: 'p1', label: 'Plate', originalPrice: 10, markedPrice: 10,
    stockQuantity: 2, categoryId: 'c1', tags: [], availablePersonalizations: [], images: []};
  beforeEach((): void => localStorage.clear());
  afterEach((): void => localStorage.clear());

  it('renders a working file selection after a restored ID is definitively rejected and survives another reload', async (): Promise<void> => {
    localStorage.setItem('natiart-cart', JSON.stringify({version: 1, items: [{cartItemId: 'line', product,
      quantity: 1, customImageUploadId: 'expired-id'}], purchases: []}));
    const create: jasmine.Spy = jasmine.createSpy().and.returnValue(throwError(() => new HttpErrorResponse({
      status: 400, error: {code: 'CUSTOM_ARTWORK_UNAVAILABLE', orderCreated: false, uploadId: 'expired-id'}})));
    const upload: jasmine.Spy = jasmine.createSpy().and.returnValue(of({uploadId: 'fresh-id'}));
    await TestBed.configureTestingModule({imports: [CheckoutComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      {provide: AuthenticationService, useValue: {isLoggedIn$: of(true), currentUser$: of(user), fetchCurrentUser: (): unknown => of(user)}},
      {provide: OrderService, useValue: {orderProcessing$: of(false), createOrder: create}},
      {provide: ShippingService, useValue: {createQuote: (): unknown => of({quoteId: 'quote', expiresAt: '2099-01-01T00:00:00Z', itemAmount: 10, shippingAmount: 0, totalAmount: 10, items: []} as unknown as ShippingQuote)}},
      {provide: ProductService, useValue: {imageInvalidations: of(), getImage: (): unknown => of(new Blob()), uploadCustomerImage: upload}},
      {provide: PaymentService, useValue: {createPixPayment: (): unknown => of({paymentId: 'paid'})}},
    ]}).compileComponents();
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
    await (fixture.componentInstance as unknown as {loadShippingQuote(): Promise<boolean>}).loadShippingQuote();
    expect((fixture.componentInstance as unknown as {shippingQuoteFingerprint: string}).shippingQuoteFingerprint).toBe(
      (fixture.componentInstance as unknown as {currentShippingQuoteFingerprint(): string}).currentShippingQuoteFingerprint());
    await fixture.componentInstance.onProcessPixPayment(user);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Select artwork again for Plate');
    const restored: CartService = new CartService();
    expect(restored.getCartItemsSnapshot()[0].requiresArtworkReselection).toBeTrue();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('[data-artwork-reselection] input');
    const file: File = new File(['art'], 'art.png', {type: 'image/png'});
    Object.defineProperty(input, 'files', {value: [file]});
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-artwork-reselection]')).toBeNull();
    expect(TestBed.inject(CartService).getCartItemsSnapshot()[0].image).toBe(file);
    create.and.returnValue(of({id: 'new-order', totalAmount: 10}));
    await (fixture.componentInstance as unknown as {loadShippingQuote(): Promise<boolean>}).loadShippingQuote();
    expect((fixture.componentInstance as unknown as {shippingQuoteFingerprint: string}).shippingQuoteFingerprint).toBe(
      (fixture.componentInstance as unknown as {currentShippingQuoteFingerprint(): string}).currentShippingQuoteFingerprint());
    await fixture.componentInstance.onProcessPixPayment(user);
    expect(upload).toHaveBeenCalledOnceWith(file);
    expect(create.calls.mostRecent().args[0].items[0].personalization.personalizationOptions.CUSTOM_IMAGE).toBe('fresh-id');
  });
});
