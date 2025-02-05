import {Component, OnDestroy, OnInit} from '@angular/core';
import {AsyncPipe, CurrencyPipe, NgForOf, NgIf} from "@angular/common";
import {CartItem} from "../../../models/CartItem.model";
import {Observable, Subscription} from "rxjs";
import {CartService} from "../../../service/cart.service";
import {FormsModule} from "@angular/forms";
import {ProductService} from "../../../service/product.service";
import {DomSanitizer, SafeUrl} from "@angular/platform-browser";
import {RouterLink} from "@angular/router";

@Component({
    selector: 'app-cart-modal',
    imports: [
        AsyncPipe,
        CurrencyPipe,
        NgIf,
        NgForOf,
        FormsModule,
        RouterLink
    ],
    templateUrl: './cart-modal.component.html'
})
export class CartModalComponent implements OnInit, OnDestroy {
  cartItems$: Observable<CartItem[]>;
  cartTotal$: Observable<number>;
  imageUrls: { [productId: string]: SafeUrl | null } = {};
  private subscriptions: Subscription[] = [];

  constructor(
    private cartService: CartService,
    private productService: ProductService,
    private sanitizer: DomSanitizer
  ) {
    this.cartItems$ = this.cartService.getCartItems();
    this.cartTotal$ = this.cartService.getCartTotal();
  }

  ngOnInit(): void {
    this.loadProductImages();
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
  }

  updateQuantity(item: CartItem, newQuantity: number) {
    if (newQuantity < 1) {
      newQuantity = 1;
    } else if (newQuantity > item.product.stockQuantity) {
      newQuantity = item.product.stockQuantity;
    }
    this.cartService.updateItemQuantity(item.product.id!, newQuantity);
  }

  removeItem(item: CartItem, event: Event) {
    event.stopPropagation()
    this.cartService.removeFromCart(item.product.id!);
  }

  onImageError(productId: string): void {
    this.imageUrls[productId] = null;
  }

  private loadProductImages(): void {
    const subscription = this.cartItems$.subscribe(items => {
      items.forEach(item => {
        if (item.product.images && item.product.images.length > 0) {
          this.fetchImage(item.product.id!, item.product.images[0]);
        }
      });
    });
    this.subscriptions.push(subscription);
  }

  private fetchImage(productId: string, imagePath: string): void {
    const subscription = this.productService.getImage(imagePath).subscribe(blob => {
      const objectUrl = URL.createObjectURL(blob);
      this.imageUrls[productId] = this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl);
    });
    this.subscriptions.push(subscription);
  }
}
