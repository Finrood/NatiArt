import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import { AsyncPipe, CurrencyPipe } from "@angular/common";
import {CartItem} from "../../../models/CartItem.model";
import {Observable, Subscription} from "rxjs";
import {CartService} from "../../../service/cart.service";
import {FormsModule} from "@angular/forms";
import {ProductService} from "../../../service/product.service";
import {DomSanitizer, SafeUrl} from "@angular/platform-browser";
import {RouterLink} from "@angular/router";
import {ButtonComponent} from "../../../../shared/components/button.component";

@Component({
    selector: 'app-cart-modal',
    imports: [
    AsyncPipe,
    CurrencyPipe,
    FormsModule,
    RouterLink,
    ButtonComponent
],
    templateUrl: './cart-modal.component.html'
})
export class CartModalComponent implements OnInit, OnDestroy {
  cartItems$: Observable<CartItem[]>;
  cartTotal$: Observable<number>;
  imageUrls: { [cartItemId: string]: SafeUrl | null } = {};
  private subscriptions: Subscription[] = [];
  private rawObjectUrlsByLine: Map<string, string> = new Map();

  private readonly _cartService: CartService = inject(CartService);
  private readonly _productService: ProductService = inject(ProductService);
  private readonly _sanitizer: DomSanitizer = inject(DomSanitizer);

  constructor() {
    this.cartItems$ = this._cartService.getCartItems();
    this.cartTotal$ = this._cartService.getCartTotal();
  }

  ngOnInit(): void {
    this.loadProductImages();
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
    this.revokeAllObjectUrls();
  }

  updateQuantity(item: CartItem, newQuantity: number): void {
    if (newQuantity < 1) {
      newQuantity = 1;
    } else if (newQuantity > item.product.stockQuantity) {
      newQuantity = item.product.stockQuantity;
    }
    this._cartService.updateItemQuantity(item.cartItemId, newQuantity);
  }

  removeItem(item: CartItem, event: Event): void {
    event.stopPropagation()
    this._cartService.removeFromCart(item.cartItemId);
  }

  onImageError(cartItemId: string): void {
    this.revokeObjectUrl(cartItemId);
    this.imageUrls[cartItemId] = null;
  }

  private revokeObjectUrl(cartItemId: string): void {
    const rawUrl: string | undefined = this.rawObjectUrlsByLine.get(cartItemId);
    if (rawUrl) {
      URL.revokeObjectURL(rawUrl);
      this.rawObjectUrlsByLine.delete(cartItemId);
    }
  }

  private revokeAllObjectUrls(): void {
    this.rawObjectUrlsByLine.forEach((rawUrl: string) => URL.revokeObjectURL(rawUrl));
    this.rawObjectUrlsByLine.clear();
  }

  private loadProductImages(): void {
    const subscription: Subscription = this.cartItems$.subscribe(items => {
      const liveIds: Set<string> = new Set(items.map(item => item.cartItemId));
      Array.from(this.rawObjectUrlsByLine.keys()).forEach((cartItemId: string) => {
        if (!liveIds.has(cartItemId)) {
          this.revokeObjectUrl(cartItemId);
          delete this.imageUrls[cartItemId];
        }
      });
      items.forEach(item => {
        // Skip lines already loading/loaded: without this guard every cart
        // emission re-issues GET image for all lines (siblings cart/order-summary
        // already guard on imageUrls[cartItemId]).
        if (this.imageUrls[item.cartItemId]) {
          return;
        }
        if (item.product.images && item.product.images.length > 0) {
          this.fetchImage(item.cartItemId, item.product.images[0]);
        }
      });
    });
    this.subscriptions.push(subscription);
  }

  private fetchImage(cartItemId: string, imagePath: string): void {
    const subscription: Subscription = this._productService.getImage(imagePath).subscribe(blob => {
      this.revokeObjectUrl(cartItemId);
      const objectUrl: string = URL.createObjectURL(blob);
      this.rawObjectUrlsByLine.set(cartItemId, objectUrl);
      this.imageUrls[cartItemId] = this._sanitizer.bypassSecurityTrustResourceUrl(objectUrl);
    });
    this.subscriptions.push(subscription);
  }
}
