// START OF FILE: src/app/service/cart.service.ts
import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable, of} from "rxjs";
import {map} from "rxjs/operators";
import {Product} from "../models/product.model";
import {CartItem} from "../models/CartItem.model";

@Injectable({
  providedIn: 'root'
})
export class CartService {
  private cartItems: CartItem[] = [];
  // Use a unique identifier for the localStorage key to avoid conflicts if needed
  private localStorageKey = 'natiart-cart';
  private cartItemsSubject = new BehaviorSubject<CartItem[]>([]);
  private cartTotalSubject = new BehaviorSubject<number>(0); // Add this

  constructor() {
    this.loadCartFromLocalStorage();
    // Calculate initial total
    this.calculateAndEmitTotal();
  }

  getCartItems(): Observable<CartItem[]> {
    return this.cartItemsSubject.asObservable();
  }

  // New method for snapshot total
  getCartTotalSnapshot(): number {
    return this.cartTotalSubject.value;
  }

  addToCart(product: Product, quantity: number, goldBorder?: boolean, image?: File): Observable<void> {
    // If a custom image is provided, ALWAYS treat it as a new, unique item.
    if (image) {
      const newCartItemId = this.generateUniqueCartItemId();
      const newItem: CartItem = { cartItemId: newCartItemId, product, quantity, goldBorder, image };
      this.cartItems.push(newItem);
    } else {
      // If no custom image, check if an identical item (product + goldBorder) already exists.
      const existingItem = this.cartItems.find(item =>
        item.product.id === product.id &&
        item.goldBorder === goldBorder &&
        !item.image // Ensure we only group items *without* custom images
      );

      if (existingItem) {
        // Check if adding the quantity exceeds stock
        const newQuantity = Math.min(existingItem.quantity + quantity, product.stockQuantity);
        existingItem.quantity = newQuantity;
      } else {
        // Check stock before adding as a new item
        if (quantity > product.stockQuantity) {
          console.warn(`Attempted to add ${quantity} of ${product.label} but only ${product.stockQuantity} in stock.`);
          quantity = product.stockQuantity; // Adjust quantity to max available stock
        }
        if (quantity > 0) { // Only add if quantity is valid
          const newCartItemId = this.generateUniqueCartItemId();
          const newItem: CartItem = { cartItemId: newCartItemId, product, quantity, goldBorder };
          this.cartItems.push(newItem);
        } else {
          console.warn(`Cannot add ${product.label} to cart because stock is 0 or requested quantity is invalid.`);
          return of(undefined);
        }
      }
    }

    this.updateCart();
    return of(undefined);
  }

  removeFromCart(cartItemId: string): Observable<void> {
    this.cartItems = this.cartItems.filter(item => item.cartItemId !== cartItemId);
    this.updateCart();
    return of(undefined);
  }

  updateItemQuantity(cartItemId: string, quantity: number): Observable<void> {
    const itemIndex = this.cartItems.findIndex(item => item.cartItemId === cartItemId);
    if (itemIndex > -1) {
      const item = this.cartItems[itemIndex];
      const newQuantity = Math.max(1, Math.min(quantity, item.product.stockQuantity));

      if (newQuantity <= 0) {
        this.removeFromCart(cartItemId);
      } else {
        item.quantity = newQuantity;
        this.cartItems[itemIndex] = item;
        this.updateCart();
      }
    } else {
      console.warn(`Item with cartItemId ${cartItemId} not found for quantity update.`);
    }
    return of(undefined);
  }

  clearCart(): Observable<void> {
    this.cartItems = [];
    this.updateCart();
    return of(undefined);
  }

  getCartCount(): Observable<number> {
    return this.cartItemsSubject.pipe(
      map(items => items.reduce((total, item) => total + item.quantity, 0))
    );
  }

  getCartTotal(): Observable<number> {
    return this.cartTotalSubject.asObservable(); // Return the BehaviorSubject as an Observable
  }

  getCartItemsSnapshot(): CartItem[] {
    return [...this.cartItems];
  }

  private generateUniqueCartItemId(): string {
    return Date.now().toString(36) + Math.random().toString(36).substring(2);
  }

  private calculateAndEmitTotal(): void {
    const total = this.cartItems.reduce((sum, item) => sum + item.product.markedPrice * item.quantity, 0);
    this.cartTotalSubject.next(total);
  }

  private updateCart(): void {
    this.cartItems = this.cartItems.filter(item => item.quantity > 0);
    this.cartItemsSubject.next([...this.cartItems]);
    this.calculateAndEmitTotal(); // Recalculate and emit total
    this.saveCartToLocalStorage();
  }

  private saveCartToLocalStorage(): void {
    try {
      const serializableCart = this.cartItems.filter(item => !item.image);
      if (serializableCart.length === this.cartItems.length) {
        localStorage.setItem(this.localStorageKey, JSON.stringify(serializableCart));
      } else {
        console.warn("Cart contains custom images and will not be saved to localStorage.");
        localStorage.removeItem(this.localStorageKey);
      }
    } catch (e) {
      console.error("Error saving cart to localStorage", e);
    }
  }

  private loadCartFromLocalStorage(): void {
    try {
      const savedCart = localStorage.getItem(this.localStorageKey);
      if (savedCart) {
        this.cartItems = JSON.parse(savedCart);
        this.cartItemsSubject.next([...this.cartItems]);
        this.calculateAndEmitTotal(); // Calculate total after loading
      }
    } catch (e) {
      console.error("Error loading cart from localStorage", e);
      this.cartItems = [];
      localStorage.removeItem(this.localStorageKey);
      this.cartItemsSubject.next([]);
      this.calculateAndEmitTotal(); // Emit 0 total
    }
  }
}
