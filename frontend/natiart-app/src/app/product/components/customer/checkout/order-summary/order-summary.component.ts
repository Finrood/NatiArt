import {ChangeDetectionStrategy, Component, Input, OnDestroy, OnInit} from '@angular/core';
import { CurrencyPipe, DatePipe } from "@angular/common";
import {CartItem} from "../../../../models/CartItem.model";
import {DomSanitizer, SafeUrl} from "@angular/platform-browser";
import {ProductService} from "../../../../service/product.service";
import {Subject} from "rxjs";
import {takeUntil} from "rxjs/operators";
import {RouterLink} from "@angular/router";
import {ShippingQuote} from "../../../../service/shipping.service";

@Component({
  selector: 'app-order-summary',
  standalone: true,
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
  @Input() cartItems: CartItem[] | null = null;
  @Input() cartTotal: number | null = 0;
  @Input() shippingQuote: ShippingQuote | null = null;

  imageUrls: { [cartItemId: string]: SafeUrl | string } = {};
  private objectUrlsCreated: string[] = [];
  private destroy$ = new Subject<void>();

  constructor(
    private productService: ProductService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    if (this.cartItems) {
      this.prepareImageUrls(this.cartItems);
    }
  }

  ngOnChanges(): void { // Detect changes to cartItems input
    if (this.cartItems) {
      this.prepareImageUrls(this.cartItems);
    }
  }

  private prepareImageUrls(items: CartItem[]): void {
    const currentItemIds = new Set(items.map(item => item.cartItemId));
    Object.keys(this.imageUrls).forEach(cartItemId => {
      if (!currentItemIds.has(cartItemId)) {
        const url = this.imageUrls[cartItemId];
        if (typeof url === 'string' && url.startsWith('blob:')) {
          URL.revokeObjectURL(url);
          const index = this.objectUrlsCreated.indexOf(url);
          if (index > -1) this.objectUrlsCreated.splice(index, 1);
        }
        delete this.imageUrls[cartItemId];
      }
    });

    items.forEach(item => {
      if (!this.imageUrls[item.cartItemId]) {
        if (item.image instanceof File) {
          const objectUrl = URL.createObjectURL(item.image);
          this.imageUrls[item.cartItemId] = this.sanitizer.bypassSecurityTrustUrl(objectUrl);
          this.objectUrlsCreated.push(objectUrl);
        } else if (item.product.images && item.product.images.length > 0) {
          this.fetchProductImage(item.cartItemId, item.product.images[0]);
        } else {
          this.imageUrls[item.cartItemId] = 'assets/img/placeholder.png';
        }
      }
    });
  }

  private fetchProductImage(cartItemId: string, imagePath: string): void {
    // Drop resolutions that arrive after the line was removed: the cleanup
    // pass in prepareImageUrls deletes the key, and an unguarded write
    // would resurrect it (AA3). Destroy teardown is covered by takeUntil.
    this.productService.getImage(imagePath).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (blob: Blob): void => {
        if (!this.isCartLineLive(cartItemId)) {
          return;
        }
        const objectUrl: string = URL.createObjectURL(blob);
        this.imageUrls[cartItemId] = this.sanitizer.bypassSecurityTrustUrl(objectUrl);
        this.objectUrlsCreated.push(objectUrl);
      },
      error: (): void => {
        if (!this.isCartLineLive(cartItemId)) {
          return;
        }
        this.imageUrls[cartItemId] = 'assets/img/placeholder.png';
      }
    });
  }

  private isCartLineLive(cartItemId: string): boolean {
    return (this.cartItems ?? []).some((item: CartItem): boolean => item.cartItemId === cartItemId);
  }

  getItemAmount(item: CartItem): number {
    return this.shippingQuote?.items.find(quoteItem => quoteItem.productId === item.product.id)?.lineAmount
      ?? item.product.markedPrice * item.quantity;
  }

  getDisplayedItemUnitPrice(item: CartItem): number {
    return this.shippingQuote?.items.find(quoteItem => quoteItem.productId === item.product.id)?.unitPrice
      ?? item.product.markedPrice;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.objectUrlsCreated.forEach(url => URL.revokeObjectURL(url));
  }
}
