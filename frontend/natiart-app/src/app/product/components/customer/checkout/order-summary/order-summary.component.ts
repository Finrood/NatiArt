import {ImageCollection, ImageLoaderService, EMPTY_PRODUCT_IMAGE} from '../../../../service/image-loader.service';
import {ChangeDetectionStrategy, Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import { CurrencyPipe, DatePipe } from "@angular/common";
import {CartItem} from "../../../../models/CartItem.model";
import {RouterLink} from "@angular/router";
import {ShippingQuote, ShippingQuoteItem} from "../../../../service/shipping.service";
import {PersonalizationOption} from '../../../../models/support/personalization-option';

@Component({
  selector: 'app-order-summary',
  imports: [
    CurrencyPipe,
    DatePipe,
    RouterLink
],
  templateUrl: './order-summary.component.html',
  // styleUrls: ['./order-summary.component.css'] // Keep if you have specific styles
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OrderSummaryComponent implements OnInit, OnDestroy {
  readonly images: ImageCollection = inject(ImageLoaderService).create();
  readonly emptyImage: string = EMPTY_PRODUCT_IMAGE;
  get imageUrls(): Record<string, string> { return this.images.urls(); }

  readonly calculatedAfterAddress: string = $localize`Calculated after shipping address`;
  @Input() cartItems: CartItem[] | null = null;
  @Input() cartTotal: number | null = 0;
  @Input() shippingQuote: ShippingQuote | null = null;

  ngOnInit(): void { this.prepareImageUrls(this.cartItems ?? []); }
  ngOnChanges(): void { this.prepareImageUrls(this.cartItems ?? []); }
  ngOnDestroy(): void { this.images.destroy(); }
  private prepareImageUrls(items: CartItem[]): void {
    this.images.update(items.map((item: CartItem) => ({key: item.cartItemId, source: item.image || item.product.images?.[0]})));
  }

  getItemAmount(item: CartItem): number {
    return this.quoteItemFor(item)?.lineAmount
      ?? item.product.markedPrice * item.quantity;
  }

  getDisplayedItemUnitPrice(item: CartItem): number {
    return this.quoteItemFor(item)?.unitPrice
      ?? item.product.markedPrice;
  }

  private quoteItemFor(item: CartItem): ShippingQuoteItem | undefined {
    const options: string[] = [];
    if (item.goldBorder) {
      options.push(`${PersonalizationOption.GOLDEN_BORDER}=true`);
    }
    if (item.customImageUploadId) {
      options.push(`${PersonalizationOption.CUSTOM_IMAGE}=${item.customImageUploadId}`);
    }
    const personalizationKey = options.sort().join('|');
    return this.shippingQuote?.items.find(quoted => quoted.productId === item.product.id
      && (quoted.personalizationKey ?? '') === personalizationKey);
  }

}
