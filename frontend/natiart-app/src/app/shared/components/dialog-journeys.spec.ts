import {of} from 'rxjs';
import {CartComponent} from '../../product/components/customer/cart/cart.component';
import {CartService} from '../../product/service/cart.service';
import {AuthenticationService} from '../../directory/service/authentication.service';
import {Type} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {CategoryManagementComponent} from '../../product/components/admin/admin-category-management/admin-category-management.component';
import {PackageManagementComponent} from '../../product/components/admin/admin-package-management/admin-package-management.component';
import {ProductManagementComponent} from '../../product/components/admin/admin-product-management/admin-product-management.component';
import {PersonalizationModalComponent} from '../../product/components/customer/personalization-modal/personalization-modal.component';
import {PersonalizationOption} from '../../product/models/support/personalization-option';

for (const [component, name] of [[CategoryManagementComponent, 'Category'], [PackageManagementComponent, 'Package'], [ProductManagementComponent, 'Product']] as const) {
  describe(name + ' editor keyboard boundary', () => {
    beforeEach(async () => {await TestBed.configureTestingModule({imports: [component], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]}).compileComponents();});
    it('opens a contained modal with labelled inputs and restores focus on Escape dismissal', () => {
      const fixture = TestBed.createComponent(component as Type<unknown>);
      fixture.detectChanges();
      const http = TestBed.inject(HttpTestingController);
      for (const request of http.match(() => true)) request.flush(request.request.url.endsWith('/page') ? {items: [], page: 0, size: 20, total: 0, hasNext: false} : []);
      fixture.detectChanges();
      const opener: HTMLButtonElement = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
        .find((button) => button.textContent?.trim() === 'Add New ' + name)!;
      opener.focus();
      opener.click();
      fixture.detectChanges();
      const dialog: HTMLDialogElement = fixture.nativeElement.querySelector('dialog');
      expect(dialog.matches(':modal')).toBeTrue();
      expect(dialog.contains(document.activeElement)).toBeTrue();
      const label: HTMLLabelElement = dialog.querySelector('label')!;
      label.click();
      expect((document.activeElement as HTMLElement).id).toBe(label.htmlFor);
      dialog.dispatchEvent(new Event('cancel', {cancelable: true}));
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('dialog')).toBeNull();
      expect(document.activeElement).toBe(opener);
      fixture.destroy();
      http.verify();
    });
  });
}

describe('Personalization dialog boundary', () => {
  beforeEach(async () => {await TestBed.configureTestingModule({imports: [PersonalizationModalComponent]}).compileComponents();});
  it('labels its file/checkbox options and dismisses the contained modal', () => {
    const fixture = TestBed.createComponent(PersonalizationModalComponent);
    fixture.componentRef.setInput('product', {id: 'art', label: 'Art', availablePersonalizations: [PersonalizationOption.GOLDEN_BORDER, PersonalizationOption.CUSTOM_IMAGE]});
    fixture.componentRef.setInput('show', true);
    fixture.detectChanges();
    const dialog: HTMLDialogElement = fixture.nativeElement.querySelector('dialog');
    expect(dialog.matches(':modal')).toBeTrue();
    for (const label of Array.from(dialog.querySelectorAll('label'))) expect(dialog.querySelector('#' + label.htmlFor)).not.toBeNull();
    const closed = spyOn(fixture.componentInstance.close, 'emit');
    dialog.dispatchEvent(new Event('cancel', {cancelable: true}));
    expect(closed).toHaveBeenCalled();
    fixture.destroy();
  });
});

describe('Cart keyboard confirmation boundary', () => {
  it('renders an imperative cart confirmation and returns focus without removing an item on Escape', async () => {
    const remove = jasmine.createSpy('remove');
    const product = {id: 'art', label: 'Art', originalPrice: 10, markedPrice: 10, stockQuantity: 3, categoryId: 'c', images: [], tags: new Set<string>(), availablePersonalizations: []};
    await TestBed.configureTestingModule({imports: [CartComponent], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      {provide: CartService, useValue: {getCartItems: () => of([{cartItemId: 'line', quantity: 1, product}]), getCartTotal: () => of(10), getCartCount: () => of(1), removeFromCart: remove}},
      {provide: AuthenticationService, useValue: {isLoggedIn$: of(true)}}]}).compileComponents();
    const fixture = TestBed.createComponent(CartComponent);
    fixture.detectChanges();
    const opener: HTMLButtonElement = Array.from(fixture.nativeElement.querySelectorAll('button[aria-label="Remove item"]') as NodeListOf<HTMLButtonElement>)
      .find((button) => button.getClientRects().length > 0)!;
    expect(opener).not.toBeNull();
    opener.focus();
    expect(document.activeElement).toBe(opener);
    opener.click();
    fixture.detectChanges();
    const dialog: HTMLDialogElement = fixture.nativeElement.querySelector('dialog');
    expect(dialog.matches(':modal')).toBeTrue();
    expect(dialog.textContent).toContain('Art');
    dialog.dispatchEvent(new Event('cancel', {cancelable: true}));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('dialog')).toBeNull();
    expect(remove).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(opener);
    fixture.destroy();
    TestBed.inject(HttpTestingController).verify();
  });
});
