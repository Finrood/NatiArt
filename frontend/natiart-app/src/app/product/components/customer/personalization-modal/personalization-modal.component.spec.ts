import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';

import { PersonalizationModalComponent } from './personalization-modal.component';
import { Product } from '../../../models/product.model';
import { PersonalizationOption } from '../../../models/support/personalization-option';

describe('PersonalizationModalComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PersonalizationModalComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(PersonalizationModalComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('returns false from both getters when the options array is missing (AA2)', () => {
    const fixture = TestBed.createComponent(PersonalizationModalComponent);
    const component: PersonalizationModalComponent = fixture.componentInstance;
    component.product = {
      id: 'p-1',
      label: 'Bare',
      originalPrice: 10,
      markedPrice: 8,
      stockQuantity: 3,
      categoryId: 'cat-1',
      availablePersonalizations: undefined as unknown as PersonalizationOption[],
      tags: new Set<string>(),
      images: [],
    };
    expect((): boolean => component.canAddGoldBorder).not.toThrow();
    expect((): boolean => component.canAddCustomImage).not.toThrow();
    expect(component.canAddGoldBorder).toBeFalse();
    expect(component.canAddCustomImage).toBeFalse();
    expect(component.isValid()).toBeTrue();
  });

  it('reflects offered options and requires a custom image when offered (AA2)', () => {
    const fixture = TestBed.createComponent(PersonalizationModalComponent);
    const component: PersonalizationModalComponent = fixture.componentInstance;
    component.product = {
      id: 'p-2',
      label: 'Fancy',
      originalPrice: 20,
      markedPrice: 18,
      stockQuantity: 5,
      categoryId: 'cat-1',
      availablePersonalizations: [PersonalizationOption.GOLDEN_BORDER, PersonalizationOption.CUSTOM_IMAGE],
      tags: new Set<string>(),
      images: [],
    };
    expect(component.canAddGoldBorder).toBeTrue();
    expect(component.canAddCustomImage).toBeTrue();
    expect(component.isValid()).toBeFalse();
    component.customImage = new File(['x'], 'custom.png', { type: 'image/png' });
    expect(component.isValid()).toBeTrue();
  });
});
