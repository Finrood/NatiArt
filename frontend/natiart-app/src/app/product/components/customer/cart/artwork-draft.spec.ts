import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {CartComponent} from './cart.component';
import {PersonalizationModalComponent} from '../personalization-modal/personalization-modal.component';
import {CartService} from '../../../service/cart.service';
import {Product} from '../../../models/product.model';
import {PersonalizationOption} from '../../../models/support/personalization-option';

describe('Artwork draft recovery across reload', (): void => {
  beforeEach((): void => localStorage.clear());
  afterEach((): void => localStorage.clear());
  it('warns at selection and in cart, preserves the line after reload and reselects its file', async (): Promise<void> => {
    await TestBed.configureTestingModule({imports: [CartComponent, PersonalizationModalComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]}).compileComponents();
    const cart: CartService = TestBed.inject(CartService);
    const product: Product = {id: 'p1', label: 'Plate', originalPrice: 10, markedPrice: 10,
      stockQuantity: 5, categoryId: 'c1', tags: new Set<string>(), images: [],
      availablePersonalizations: [PersonalizationOption.CUSTOM_IMAGE]};
    cart.addToCart({...product, id: 'ordinary'}, 1).subscribe();
    const modal = TestBed.createComponent(PersonalizationModalComponent);
    modal.componentRef.setInput('show', true);
    modal.componentRef.setInput('product', product);
    modal.componentInstance.personalize.subscribe(options => cart.addToCart(product, 2, options.goldBorder, options.customImage).subscribe());
    modal.detectChanges();
    expect(modal.nativeElement.textContent).toContain('If you reload, select the file again.');
    const file: File = new File(['art'], 'art.png', {type: 'image/png'});
    const input: HTMLInputElement = modal.nativeElement.querySelector('input[type=file]');
    Object.defineProperty(input, 'files', {value: [file]});
    input.dispatchEvent(new Event('change'));
    modal.detectChanges();
    const buttons: HTMLButtonElement[] = Array.from(modal.nativeElement.querySelectorAll('button'));
    buttons.find(button => button.textContent?.includes('Add to Cart'))!.click();
    const lineId: string = cart.getCartItemsSnapshot()[1].cartItemId;
    const view = TestBed.createComponent(CartComponent);
    view.detectChanges();
    expect(view.nativeElement.textContent).toContain('Artwork uploads at checkout.');
    view.destroy(); modal.destroy();
    const restored: CartService = new CartService();
    expect(restored.getCartItemsSnapshot().map(item => item.product.id)).toEqual(['ordinary', 'p1']);
    expect(restored.getCartItemsSnapshot()[1].requiresArtworkReselection).toBeTrue();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({imports: [CartComponent], providers: [provideHttpClient(),
      provideHttpClientTesting(), provideRouter([]), {provide: CartService, useValue: restored}]}).compileComponents();
    const reloaded = TestBed.createComponent(CartComponent); reloaded.detectChanges();
    const picker: HTMLInputElement = reloaded.nativeElement.querySelector('[data-artwork-reselection] input');
    Object.defineProperty(picker, 'files', {value: [file]}); picker.dispatchEvent(new Event('change'));
    reloaded.detectChanges();
    expect(restored.getCartItemsSnapshot()[1]).toEqual(jasmine.objectContaining({cartItemId: lineId, quantity: 2, image: file, requiresArtworkReselection: false}));
    expect(reloaded.nativeElement.querySelector('[data-artwork-reselection]')).toBeNull();
  });
});
