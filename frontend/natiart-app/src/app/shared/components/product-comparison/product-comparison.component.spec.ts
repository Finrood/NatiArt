import {ComponentFixture, TestBed} from '@angular/core/testing';
import {HttpRequest, provideHttpClient} from '@angular/common/http';
import {HttpTestingController, TestRequest, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, Router} from '@angular/router';
import {ProductComparisonComponent} from './product-comparison.component';
import {ProductComparisonService} from '../../../product/service/product-comparison.service';
import {Product} from '../../../product/models/product.model';
import {PersonalizationOption} from '../../../product/models/support/personalization-option';
import {environment} from '../../../../environments/environment';

const piece = (id: string): Product => ({id, label: 'Piece ' + id, categoryId: 'c', originalPrice: 100,
  markedPrice: 90, stockQuantity: 4, availablePersonalizations: [], images: [], tags: []});

describe('Viewing table recovery and lifetime', (): void => {
  let fixture: ComponentFixture<ProductComparisonComponent>;
  let http: HttpTestingController;
  let comparison: ProductComparisonService;
  const api: string = environment.api.product.url;
  beforeEach(async (): Promise<void> => {
    sessionStorage.removeItem('natiart-comparison-v1');
    await TestBed.configureTestingModule({imports: [ProductComparisonComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(), provideRouter([
        {path: 'products', children: []}, {path: 'product/:id', children: []}, {path: 'checkout', children: []}])
    ]}).compileComponents();
    http = TestBed.inject(HttpTestingController);
    comparison = TestBed.inject(ProductComparisonService);
    comparison.toggle(piece('a'), {categoryId: 'c', query: 'gift', page: 2});
    comparison.toggle(piece('b'), {});
    fixture = TestBed.createComponent(ProductComparisonComponent);
    fixture.componentInstance.$shopping.set(true);
    fixture.detectChanges();
  });
  afterEach((): void => { fixture.destroy(); http.verify(); sessionStorage.removeItem('natiart-comparison-v1'); });

  function open(): void {
    const root: HTMLElement = fixture.nativeElement;
    root.querySelector<HTMLButtonElement>('.tray-compare')!.click();
    fixture.detectChanges();
  }

  it('uses fresh server prices, stock and required/included options instead of selection snapshots', (): void => {
    open();
    const refreshed: TestRequest = http.expectOne(`${api}/products/a`);
    expect(refreshed.request.cache).toBe('no-store');
    refreshed.flush({...piece('a'), markedPrice: 23.5, stockQuantity: 0, categoryLabel: 'Porcelain',
      hasFixedGoldenBorder: true, availablePersonalizations: [PersonalizationOption.CUSTOM_IMAGE]});
    http.expectOne(`${api}/products/b`).flush({...piece('b'), availablePersonalizations: [PersonalizationOption.GOLDEN_BORDER]});
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('.comparison-price')?.textContent).toContain('23.50');
    expect(root.textContent).toContain('Out of Stock');
    expect(root.textContent).toContain('Required');
    expect(root.textContent).toContain('Included');
    expect(root.textContent).toContain('Optional');
    expect(root.querySelector('a')?.href).toContain('/product/a?categoryId=c&query=gift&page=2');
    expect(root.querySelectorAll('table th[scope="col"]').length).toBe(2);
    expect(root.querySelector('dialog')?.open).toBeTrue();
  });

  it('keeps the checked piece visible while retrying a failed neighbor without trusting its old price or losing focus', async (): Promise<void> => {
    open();
    http.expectOne(`${api}/products/a`).flush({...piece('a'), markedPrice: 20});
    http.expectOne(`${api}/products/b`).flush('offline', {status: 503, statusText: 'Unavailable'});
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('This piece could not be checked');
    expect(fixture.nativeElement.querySelectorAll('.comparison-price')[1].textContent.trim()).toBe('—');
    const retry: HTMLButtonElement = fixture.nativeElement.querySelector('.retry-piece');
    retry.focus();
    retry.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('#comparison-piece-b'));
    http.expectNone(`${api}/products/a`);
    http.expectOne(`${api}/products/b`).flush({...piece('b'), markedPrice: 15});
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.comparison-price')[1].textContent).toContain('15.00');
  });

  it('cancels pending product/image requests on close, revokes owned URLs and rechecks on reopening', (): void => {
    const revoked: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    open();
    http.expectOne(`${api}/products/a`).flush({...piece('a'), images: ['a.webp']});
    const firstImage: TestRequest = http.expectOne((request: HttpRequest<unknown>): boolean => request.url === `${api}/images`);
    firstImage.flush(new Blob(['art'], {type: 'image/webp'}));
    const ownedUrl: string = fixture.componentInstance.image('a');
    const pending: TestRequest = http.expectOne(`${api}/products/b`);
    fixture.componentInstance.close();
    fixture.detectChanges();
    expect(pending.cancelled).toBeTrue();
    expect(revoked).toHaveBeenCalledWith(ownedUrl);
    open();
    http.expectOne(`${api}/products/a`).flush({...piece('a'), images: ['a.webp']});
    const delayedImage: TestRequest = http.expectOne((request: HttpRequest<unknown>): boolean => request.url === `${api}/images`);
    const delayedProduct: TestRequest = http.expectOne(`${api}/products/b`);
    fixture.componentInstance.close();
    fixture.detectChanges();
    expect(delayedImage.cancelled).toBeTrue();
    expect(delayedProduct.cancelled).toBeTrue();
  });

  it('restores the opener on Escape and a stable tray heading when removing a column', async (): Promise<void> => {
    const opener: HTMLButtonElement = fixture.nativeElement.querySelector('.tray-compare');
    opener.focus();
    open();
    const pendingA: TestRequest = http.expectOne(`${api}/products/a`);
    const pendingB: TestRequest = http.expectOne(`${api}/products/b`);
    fixture.nativeElement.querySelector('dialog').dispatchEvent(new Event('cancel', {cancelable: true}));
    fixture.detectChanges();
    expect(document.activeElement).toBe(opener);
    expect(pendingA.cancelled && pendingB.cancelled).toBeTrue();
    open();
    http.expectOne(`${api}/products/a`).flush(piece('a'));
    http.expectOne(`${api}/products/b`).flush(piece('b'));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.remove-piece').click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(comparison.$pieces().length).toBe(1);
    expect(fixture.nativeElement.querySelector('dialog')).toBeNull();
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('.tray-copy h2'));
  });

  it('dismisses on navigation and hides the table during checkout without discarding the pair', async (): Promise<void> => {
    open();
    const pendingA: TestRequest = http.expectOne(`${api}/products/a`);
    const pendingB: TestRequest = http.expectOne(`${api}/products/b`);
    await TestBed.inject(Router).navigateByUrl('/checkout');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.comparison-tray')).toBeNull();
    expect(fixture.nativeElement.querySelector('dialog')).toBeNull();
    expect(pendingA.cancelled && pendingB.cancelled).toBeTrue();
    expect(comparison.$pieces().length).toBe(2);
    await TestBed.inject(Router).navigateByUrl('/products?page=3');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.tray-compare').disabled).toBeFalse();
    http.expectNone(`${api}/products/a`);
  });
});
