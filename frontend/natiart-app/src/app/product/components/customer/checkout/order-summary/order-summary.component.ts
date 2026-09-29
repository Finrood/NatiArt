import {ImageCollection, ImageLoaderService, EMPTY_PRODUCT_IMAGE} from '../../../../service/image-loader.service';
import {ChangeDetectionStrategy, Component, inject, Input, OnDestroy, OnInit} from '@angular/core';
import { CurrencyPipe } from "@angular/common";
import {CartItem} from "../../../../models/CartItem.model";
import {RouterLink} from "@angular/router";

@Component({
  selector: 'app-order-summary',
  imports: [
    CurrencyPipe,
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

  @Input() cartItems: CartItem[] | null = null;
  @Input() cartTotal: number | null = 0;

  ngOnInit(): void { this.prepareImageUrls(this.cartItems ?? []); }
  ngOnChanges(): void { this.prepareImageUrls(this.cartItems ?? []); }
  ngOnDestroy(): void { this.images.destroy(); }
  private prepareImageUrls(items: CartItem[]): void {
    this.images.update(items.map((item: CartItem) => ({key: item.cartItemId, source: item.image || item.product.images?.[0]})));
  }
}
