// START OF FILE: src/app/service/cart.service.ts
import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable, of} from "rxjs";
import {map} from "rxjs/operators";
import {Product} from "../models/product.model";
import {CartItem} from "../models/CartItem.model";
import {reportError, reportWarning} from '../../shared/service/error-reporting.service';

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
    if (!Number.isInteger(quantity) || quantity <= 0 || product.stockQuantity <= 0) {
      reportWarning('cart');
      return of(undefined);
    }
    quantity = Math.min(quantity, product.stockQuantity);

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
        !item.image &&
        !item.customImageUploadId // Uploaded artwork remains its own fulfillment line.
      );

      if (existingItem) {
        // Check if adding the quantity exceeds stock
        const newQuantity = Math.min(existingItem.quantity + quantity, product.stockQuantity);
        existingItem.quantity = newQuantity;
      } else {
        // Check stock before adding as a new item
        if (quantity > product.stockQuantity) {
          reportWarning('cart');
          quantity = product.stockQuantity; // Adjust quantity to max available stock
        }
        if (quantity > 0) { // Only add if quantity is valid
          const newCartItemId = this.generateUniqueCartItemId();
          const newItem: CartItem = { cartItemId: newCartItemId, product, quantity, goldBorder };
          this.cartItems.push(newItem);
        } else {
          reportWarning('cart');
          return of(undefined);
        }
      }
    }

    this.updateCart();
    return of(undefined);
  }

  setCustomImageUploadId(cartItemId: string, uploadId: string): Observable<void> {
    if (!uploadId || uploadId.trim().length === 0) {
      reportWarning('cart');
      return of(undefined);
    }
    const item = this.cartItems.find(candidate => candidate.cartItemId === cartItemId);
    if (!item) {
      reportWarning('cart');
      return of(undefined);
    }
    item.customImageUploadId = uploadId;
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
      // Removal is removeFromCart's job: quantities floor at 1 so a 0 update
      // can never silently mean delete (BW1 dead-branch fix).
      const newQuantity: number = Math.max(1, Math.min(quantity, item.product.stockQuantity));
      item.quantity = newQuantity;
      this.cartItems[itemIndex] = item;
      this.updateCart();
    } else {
      reportWarning('cart');
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
      // Files cannot survive a reload. Keep ordinary lines and already-uploaded
      // artwork lines, while leaving an in-memory draft out of storage.
      const serializableCart = this.cartItems
        .filter(item => !item.image || !!item.customImageUploadId)
        .map(({image: _image, ...item}) => item);
      if (serializableCart.length > 0 || this.cartItems.length === 0) {
        localStorage.setItem(this.localStorageKey, JSON.stringify(serializableCart));
      } else {
        reportWarning('storage');
        localStorage.removeItem(this.localStorageKey);
      }
    } catch (e) {
      reportError('storage', e);
    }
  }

  private loadCartFromLocalStorage(): void {
    try {
      const savedCart = localStorage.getItem(this.localStorageKey);
      if (savedCart) {
        const parsed: unknown = JSON.parse(savedCart);
        // AS1: drop corrupt-but-parseable shape before emit — a missing or
        // duplicate cartItemId collapses map keys so remove/quantity ops hit
        // every line at once or none, and a null product NPEs the total.
        const restored: CartItem[] = this.sanitizeRestoredCart(parsed);
        this.cartItems = restored;
        this.cartItemsSubject.next([...this.cartItems]);
        this.calculateAndEmitTotal(); // Calculate total after loading
      }
    } catch (e) {
      reportError('storage', e);
      this.cartItems = [];
      localStorage.removeItem(this.localStorageKey);
      this.cartItemsSubject.next([]);
      this.calculateAndEmitTotal(); // Emit 0 total
    }
  }

  private sanitizeRestoredCart(parsed: unknown): CartItem[] {
    if (!Array.isArray(parsed)) {
      return [];
    }
    const seenIds: Set<string> = new Set<string>();
    const valid: CartItem[] = [];
    for (const entry of parsed) {
      if (!this.isRestorableCartLine(entry)) {
        continue;
      }
      const line: CartItem = entry as CartItem;
      if (seenIds.has(line.cartItemId)) {
        // Duplicate identity: mint a fresh id so keyed ops stay 1:1.
        line.cartItemId = this.generateUniqueCartItemId();
      }
      seenIds.add(line.cartItemId);
      valid.push(line);
    }
    return valid;
  }

  private isRestorableCartLine(entry: unknown): entry is CartItem {
    if (typeof entry !== 'object' || entry === null) {
      return false;
    }
    const line = entry as Partial<CartItem>;
    if (typeof line.cartItemId !== 'string' || line.cartItemId.trim().length === 0) {
      return false;
    }
    if (typeof line.product !== 'object' || line.product === null) {
      return false;
    }
    if (typeof line.product.id !== 'string' || line.product.id.trim().length === 0) {
      return false;
    }
    if (typeof line.quantity !== 'number' || !Number.isInteger(line.quantity) || line.quantity < 1) {
      return false;
    }
    if (line.customImageUploadId !== undefined
      && (typeof line.customImageUploadId !== 'string' || line.customImageUploadId.trim().length === 0)) {
      return false;
    }
    return true;
  }
}
