import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { of, Subject } from 'rxjs';

import { ProductManagementComponent } from './admin-product-management.component';
import { ProductService } from '../../../service/product.service';

describe('ProductManagementComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductManagementComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('tracks the golden-border valueChanges subscription so destroy unsubscribes it (P1)', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    const control = component.productForm.get('hasFixedGoldenBorder');
    expect(control).toBeTruthy();
    const stream: Subject<boolean> = (control?.valueChanges as unknown) as Subject<boolean>;
    expect(stream.observed).toBeTrue();

    fixture.destroy();
    expect(stream.observed).toBeFalse();
  });

  it('revokes product image object URLs on destroy', () => {
    const fixture = TestBed.createComponent(ProductManagementComponent);
    const component = fixture.componentInstance;
    const productService: ProductService = TestBed.inject(ProductService);
    spyOn(productService, 'getImage').and.returnValue(of(new Blob(['x'])));
    const revokeSpy = spyOn(URL, 'revokeObjectURL');

    const fetcher = component as unknown as {
      fetchImage(productId: string, imagePath: string): void;
    };
    fetcher.fetchImage('prod-1', 'img-1');
    expect(component.imageUrls['prod-1']).toBeTruthy();

    fixture.destroy();

    expect(revokeSpy).toHaveBeenCalled();
  });
});
