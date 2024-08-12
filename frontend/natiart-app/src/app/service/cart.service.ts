import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of } from "rxjs";
import { map } from "rxjs/operators";
import { Product } from "../models/product.model";
import { CartItem } from "../models/CartItem.model";

@Injectable({
  providedIn: 'root'
})
export class CartService {
  private cartItems: CartItem[] = [];
  private cartItemsSubject = new BehaviorSubject<CartItem[]>([]);

  constructor() {
    // Load cart from local storage if available
    const savedCart = localStorage.getItem('cart');
    if (savedCart) {
      this.cartItems = JSON.parse(savedCart);
      this.cartItemsSubject.next(this.cartItems);
    }
  }

  getCartItems(): Observable<CartItem[]> {
    return this.cartItemsSubject.asObservable();
  }

  addToCart(product: Product, quantity: number, goldBorder?: boolean, image?: File): Observable<void> {
    const existingItem = this.cartItems.find(item => item.product.id === product.id && item.goldBorder === goldBorder);
    if (existingItem) {
      existingItem.quantity += quantity;
    } else {
      this.cartItems.push({ product, goldBorder, image, quantity });
    }
    this.updateCart();
    return of(undefined); // Returning observable of void to indicate operation completion
  }

  removeFromCart(productId: string): Observable<void> {
    this.cartItems = this.cartItems.filter(item => item.product.id !== productId);
    this.updateCart();
    return of(undefined); // Returning observable of void to indicate operation completion
  }

  updateItemQuantity(productId: string, quantity: number): Observable<void> {
    const item = this.cartItems.find(item => item.product.id === productId);
    if (item) {
      item.quantity = quantity;
      if (item.quantity <= 0) {
        return this.removeFromCart(productId);
      } else {
        this.updateCart();
      }
    }
    return of(undefined); // Returning observable of void to indicate operation completion
  }

  clearCart(): Observable<void> {
    this.cartItems = [];
    this.updateCart();
    return of(undefined); // Returning observable of void to indicate operation completion
  }

  private updateCart(): void {
    this.cartItemsSubject.next(this.cartItems);
    localStorage.setItem('cart', JSON.stringify(this.cartItems));
  }

  getCartCount(): Observable<number> {
    return this.cartItemsSubject.pipe(
      map(items => items.reduce((total, item) => total + item.quantity, 0))
    );
  }

  getCartTotal(): Observable<number> {
    return this.cartItemsSubject.pipe(
      map(items => items.reduce((sum, item) => sum + item.product.markedPrice * item.quantity, 0))
    );
  }
}
