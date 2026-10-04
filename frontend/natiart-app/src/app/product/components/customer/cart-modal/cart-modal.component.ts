import {ImageCollection, ImageLoaderService, EMPTY_PRODUCT_IMAGE} from '../../../service/image-loader.service';
import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import { AsyncPipe, CurrencyPipe } from "@angular/common";
import {CartItem} from "../../../models/CartItem.model";
import {Observable, Subscription} from "rxjs";
import {CartService} from "../../../service/cart.service";
import {FormsModule} from "@angular/forms";
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
  readonly images: ImageCollection = inject(ImageLoaderService).create();
  readonly emptyImage: string = EMPTY_PRODUCT_IMAGE;
  get imageUrls(): Record<string, string> { return this.images.urls(); }

  cartItems$: Observable<CartItem[]>;
  cartTotal$: Observable<number>;
  private subscriptions: Subscription[] = [];

  private readonly _cartService: CartService = inject(CartService);

  constructor() {
    this.cartItems$ = this._cartService.getCartItems();
    this.cartTotal$ = this._cartService.getCartTotal();
  }

  ngOnInit(): void {
    this.loadProductImages();
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
    this.images.destroy();
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

  onImageError(key: string): void { this.images.failed(key); }
  private loadProductImages(): void {
    this.subscriptions.push(this.cartItems$.subscribe((items: CartItem[]): void => {
      this.images.update(items.map((item: CartItem) => ({key: item.cartItemId, source: item.image || item.product.images?.[0]})));
    }));
  }
}
