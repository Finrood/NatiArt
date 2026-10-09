import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {ProductCardComponent} from './product-card.component';
import {EMPTY_PRODUCT_IMAGE} from '../../../product/service/image-loader.service';
import {PersonalizationOption} from '../../../product/models/support/personalization-option';
import {Product} from '../../../product/models/product.model';
import {ProductComparisonService} from '../../../product/service/product-comparison.service';

describe('Product card image recovery', () => {
  it('selects comparison separately from the product link and leaves selected pieces removable at the limit', async (): Promise<void> => {
    sessionStorage.removeItem('natiart-comparison-v1');
    await TestBed.configureTestingModule({imports: [ProductCardComponent], providers: [provideRouter([])]}).compileComponents();
    const fixture: ComponentFixture<ProductCardComponent> = TestBed.createComponent(ProductCardComponent);
    const product: Product = {id: 'a', label: 'Porcelain', markedPrice: 8, originalPrice: 10,
      categoryId: 'c', stockQuantity: 1, images: [], tags: [], availablePersonalizations: []};
    fixture.componentRef.setInput('product', product);
    fixture.componentRef.setInput('compareEnabled', true);
    fixture.componentRef.setInput('catalogContext', {categoryId: 'c', query: 'gift', page: 1});
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    const button: HTMLButtonElement = root.querySelector('.product-compare')!;
    expect(button.closest('a')).toBeNull();
    expect(button.getAttribute('aria-label')).toBe('Compare Porcelain');
    button.click();
    fixture.detectChanges();
    const comparison: ProductComparisonService = TestBed.inject(ProductComparisonService);
    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(comparison.$pieces()[0].context.query).toBe('gift');
    comparison.toggle({...product, id: 'b'}, {});
    fixture.detectChanges();
    expect(button.disabled).toBeFalse();
    fixture.componentRef.setInput('product', {...product, id: 'c'});
    fixture.detectChanges();
    expect(button.disabled).toBeTrue();
    fixture.componentRef.setInput('product', product);
    fixture.detectChanges();
    button.click();
    fixture.detectChanges();
    expect(comparison.isSelected('a')).toBeFalse();
    fixture.destroy();
    sessionStorage.removeItem('natiart-comparison-v1');
  });

  it('prioritizes availability over sale and new badges, then restores merchandising when stock returns', async () => {
    await TestBed.configureTestingModule({imports: [ProductCardComponent], providers: [provideRouter([])]}).compileComponents();
    const fixture = TestBed.createComponent(ProductCardComponent);
    const product: Product = {id: 'p', label: 'Porcelain', markedPrice: 8, originalPrice: 10,
      categoryId: 'c', categoryLabel: 'Tableware', stockQuantity: 0, newProduct: true,
      images: [], tags: [], availablePersonalizations: []};
    fixture.componentRef.setInput('product', product);
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('.product-badge')?.textContent).toBe('Out of Stock');
    expect(root.querySelector('.product-category')?.textContent).toBe('Tableware');
    fixture.componentRef.setInput('product', {...product, stockQuantity: 2});
    fixture.detectChanges();
    expect(root.querySelector('.product-badge')?.textContent).toBe('Sale');
    fixture.componentRef.setInput('product', {...product, stockQuantity: 2, originalPrice: 8, categoryLabel: null});
    fixture.detectChanges();
    expect(root.querySelector('.product-badge')?.textContent).toBe('New');
    expect(root.querySelector('.product-category')).toBeNull();
    fixture.destroy();
  });

  it('falls back after an image fails and allows a replacement image to load', async () => {
    await TestBed.configureTestingModule({imports: [ProductCardComponent], providers: [provideRouter([])]}).compileComponents();
    const fixture = TestBed.createComponent(ProductCardComponent);
    const product: Product = {id: 'p', label: 'Porcelain', markedPrice: 8, originalPrice: 10,
      categoryId: 'c', stockQuantity: 1, images: [], tags: [],
      availablePersonalizations: [PersonalizationOption.CUSTOM_IMAGE]};
    fixture.componentRef.setInput('product', product);
    fixture.componentRef.setInput('imageUrl', '/failed.webp');
    fixture.detectChanges();
    const failed: jasmine.Spy = jasmine.createSpy('imageFailed');
    fixture.componentInstance.imageFailed.subscribe(failed);
    fixture.nativeElement.querySelector('img').dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toBe(EMPTY_PRODUCT_IMAGE);
    expect(failed).toHaveBeenCalledTimes(1);
    fixture.componentRef.setInput('imageUrl', '/replacement.webp');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toBe('/replacement.webp');
    expect(fixture.nativeElement.textContent).toContain('Only 1 left');
    expect(fixture.nativeElement.textContent).toContain('Personalizable');
    fixture.componentRef.setInput('product', {...product, stockQuantity: 0, availablePersonalizations: []});
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Out of Stock');
    expect(fixture.nativeElement.textContent).not.toContain('Personalizable');
    fixture.destroy();
  });
});
