import { Component, OnDestroy, OnInit } from '@angular/core';
import { Observable, Subscription, BehaviorSubject, combineLatest } from 'rxjs';
import { map, switchMap, tap } from 'rxjs/operators';
import { CartItem } from '../../../models/CartItem.model';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { CartService } from '../../../service/cart.service';
import { ProductService } from '../../../service/product.service';
import { RouterLink } from '@angular/router';
import { AsyncPipe, CurrencyPipe, NgForOf, NgIf } from '@angular/common';
import { ShippingEstimationComponent } from '../shipping-estimation/shipping-estimation.component';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [
    RouterLink,
    AsyncPipe,
    CurrencyPipe,
    NgForOf,
    NgIf,
    ReactiveFormsModule,
    ShippingEstimationComponent
  ],
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.css']
})
export class CartComponent implements OnInit, OnDestroy {
  cartItems$: Observable<CartItem[]>;
  cartTotal$: Observable<number>;
  imageUrls: { [productId: string]: SafeUrl | null } = {};
  isLoading$ = new BehaviorSubject<boolean>(false);
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
    this.subscriptions.push(
      this.cartItems$.pipe(
        switchMap(items => this.loadProductImages(items))
      ).subscribe()
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
  }

  updateQuantity(item: CartItem, change: number): void {
    let newQuantity = item.quantity + change;
    if(newQuantity < 1) {
      newQuantity = 1;
    } else if (newQuantity > item.product.stockQuantity) {
      newQuantity = item.product.stockQuantity;
    }
    if (newQuantity >= 1 && newQuantity <= item.product.stockQuantity) {
      this.isLoading$.next(true);
      this.cartService.updateItemQuantity(item.product.id!, newQuantity).pipe(
        tap(() => {
          this.isLoading$.next(false);
        })
      ).subscribe();
    }
  }

  confirmRemoveItem(item: CartItem): void {
    if (confirm('Are you sure you want to remove this item from the cart?')) {
      this.removeItem(item);
    }
  }

  removeItem(item: CartItem): void {
    this.isLoading$.next(true);
    this.cartService.removeFromCart(item.product.id!).pipe(
      tap(() => this.isLoading$.next(false))
    ).subscribe();
  }

  confirmClearCart(): void {
    if (confirm('Are you sure you want to clear the cart?')) {
      this.clearCart();
    }
  }

  clearCart(): void {
    this.isLoading$.next(true);
    this.cartService.clearCart().pipe(
      tap(() => this.isLoading$.next(false))
    ).subscribe();
  }

  proceedToCheckout(): void {
    if (!this.isLoading$.value) {
      console.log('Proceeding to checkout');
      //TODO
      // Implement checkout logic here
    }
  }

  private loadProductImages(items: CartItem[]): Observable<void> {
    const imageObservables = items.map(item =>
      this.fetchImage(item.product.id!, item.product.images[0])
    );
    return combineLatest(imageObservables).pipe(map(() => {}));
  }

  private fetchImage(productId: string, imagePath: string): Observable<void> {
    return this.productService.getImage(imagePath).pipe(
      tap(blob => {
        const objectUrl = URL.createObjectURL(blob);
        this.imageUrls[productId] = this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl);
      }),
      map(() => {})
    );
  }
}
