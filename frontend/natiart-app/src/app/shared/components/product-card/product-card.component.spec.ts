import {TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {ProductCardComponent} from './product-card.component';
import {EMPTY_PRODUCT_IMAGE} from '../../../product/service/image-loader.service';
import {PersonalizationOption} from '../../../product/models/support/personalization-option';
import {Product} from '../../../product/models/product.model';

describe('Product card image recovery', () => {
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
