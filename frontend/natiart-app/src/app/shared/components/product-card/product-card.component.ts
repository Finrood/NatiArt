import {Component, computed, inject, input, InputSignal, output, Signal, signal} from '@angular/core';
import {CurrencyPipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {Product} from '../../../product/models/product.model';
import {PersonalizationOption} from '../../../product/models/support/personalization-option';
import {EMPTY_PRODUCT_IMAGE} from '../../../product/service/image-loader.service';
import {AddToCartButtonComponent} from '../../../product/components/customer/add-to-cart-button/add-to-cart-button.component';
import {CatalogContext} from '../../models/catalog-context';
import {ProductComparisonService} from '../../../product/service/product-comparison.service';

@Component({selector: 'app-product-card', imports: [CurrencyPipe, RouterLink, AddToCartButtonComponent],
  templateUrl: './product-card.component.html', styleUrl: './product-card.component.css', host: {class: 'block h-full min-w-0'}})
export class ProductCardComponent {
  readonly comparison: ProductComparisonService = inject(ProductComparisonService);
  readonly $compareEnabled: InputSignal<boolean> = input<boolean>(false, {alias: 'compareEnabled'});
  readonly $imagePriority: InputSignal<boolean> = input<boolean>(false, {alias: 'imagePriority'});
  readonly $comparisonName: Signal<string> = computed<string>((): string => $localize`Compare ${this.$product().label}`);
  readonly $product = input.required<Product>({alias: 'product'});
  readonly $imageUrl = input<string>(EMPTY_PRODUCT_IMAGE, {alias: 'imageUrl'});
  readonly $imageLoading = input(false, {alias: 'imageLoading'});
  readonly $quickAdd = input(false, {alias: 'quickAdd'});
  readonly $catalogContext = input<CatalogContext>({}, {alias: 'catalogContext'});
  readonly imageFailed = output<void>();
  readonly itemAdded = output<HTMLElement>();
  private readonly $failedSource = signal<string | null>(null);
  readonly $displayImage = computed((): string => this.$failedSource() === this.$imageUrl()
    ? EMPTY_PRODUCT_IMAGE : this.$imageUrl());
  readonly $personalizable = computed((): boolean => this.$product().availablePersonalizations?.some(
    (option: PersonalizationOption): boolean => option === PersonalizationOption.GOLDEN_BORDER
      || option === PersonalizationOption.CUSTOM_IMAGE) ?? false);

  onImageError(): void {
    this.$failedSource.set(this.$imageUrl());
    this.imageFailed.emit();
  }
}
