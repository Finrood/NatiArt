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

  constructor() {
    this.loadCartFromLocalStorage();
  }

  getCartItems(): Observable<CartItem[]> {
    return this.cartItemsSubject.asObservable();
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
          // Optionally, provide user feedback here (e.g., using an alert service)
          return of(undefined); // Don't update cart if nothing can be added
        }
      }
    }

    this.updateCart();
    return of(undefined);
  }

  // Update method to use cartItemId
  removeFromCart(cartItemId: string): Observable<void> {
    this.cartItems = this.cartItems.filter(item => item.cartItemId !== cartItemId);
    this.updateCart();
    return of(undefined);
  }

  // Update method to use cartItemId
  updateItemQuantity(cartItemId: string, quantity: number): Observable<void> {
    const itemIndex = this.cartItems.findIndex(item => item.cartItemId === cartItemId);
    if (itemIndex > -1) {
      const item = this.cartItems[itemIndex];
      // Ensure quantity doesn't exceed stock
      const newQuantity = Math.max(1, Math.min(quantity, item.product.stockQuantity));

      if (newQuantity <= 0) {
        // This case should ideally be handled by removing the item,
        // but we'll keep it above 0 based on Math.max(1, ...)
        // If you want quantity 0 to remove, call removeFromCart here.
        console.warn(`Quantity for item ${cartItemId} reached 0 or less, removing.`);
        this.removeFromCart(cartItemId); // Remove if quantity becomes 0 or less
      } else {
        item.quantity = newQuantity;
        this.cartItems[itemIndex] = item; // Ensure change detection if needed (though should be fine)
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
    return this.cartItemsSubject.pipe(
      map(items => items.reduce((sum, item) => sum + item.product.markedPrice * item.quantity, 0))
    );
  }

  // Helper to get a snapshot if needed elsewhere (like checkout)
  getCartItemsSnapshot(): CartItem[] {
    return [...this.cartItems]; // Return a copy
  }

  // Helper to generate a simple unique ID for the cart item
  private generateUniqueCartItemId(): string {
    return Date.now().toString(36) + Math.random().toString(36).substring(2);
  }

  private updateCart(): void {
    // Filter out any items that might have ended up with quantity 0 or less
    this.cartItems = this.cartItems.filter(item => item.quantity > 0);
    this.cartItemsSubject.next([...this.cartItems]); // Emit a new array reference
    this.saveCartToLocalStorage();
  }

  private saveCartToLocalStorage(): void {
    try {
      // We cannot directly stringify File objects. We need to handle them.
      // For simplicity here, we'll just *not* save carts with File objects to localStorage.
      // A more complex solution would involve FileReader to store base64, but that's heavy.
      const serializableCart = this.cartItems.filter(item => !item.image);
      if (serializableCart.length === this.cartItems.length) {
        localStorage.setItem(this.localStorageKey, JSON.stringify(serializableCart));
      } else {
        // If there are custom images, don't persist to avoid issues.
        // The cart will reset on refresh if it contains custom images.
        console.warn("Cart contains custom images and will not be saved to localStorage.");
        localStorage.removeItem(this.localStorageKey); // Clear potentially outdated saved cart
      }

    } catch (e) {
      console.error("Error saving cart to localStorage", e);
    }
  }

  private loadCartFromLocalStorage(): void {
    try {
      const savedCart = localStorage.getItem(this.localStorageKey);
      if (savedCart) {
        // We assume the saved cart only contains items without File objects
        this.cartItems = JSON.parse(savedCart);
        this.cartItemsSubject.next([...this.cartItems]);
      }
    } catch (e) {
      console.error("Error loading cart from localStorage", e);
      this.cartItems = []; // Reset cart on error
      localStorage.removeItem(this.localStorageKey); // Clear corrupted data
      this.cartItemsSubject.next([]);
    }
  }
}
