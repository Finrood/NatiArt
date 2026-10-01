// START OF FILE: src/app/service/cart.service.ts
import {Injectable} from '@angular/core';
import {BehaviorSubject, Observable, of} from "rxjs";
import {map} from "rxjs/operators";
import {Product} from "../models/product.model";
import {CartItem} from "../models/CartItem.model";
import {reportError, reportWarning} from '../../shared/service/error-reporting.service';

export interface PurchasedCartLine { cartItemId: string; quantity: number; }
interface CartPurchase { orderId: string; customerId: string; lines: PurchasedCartLine[]; completed: boolean; }

@Injectable({
  providedIn: 'root'
})
export class CartService {
  private static readonly storageVersion = 1;
  private cartItems: CartItem[] = [];
  private purchases: CartPurchase[] = [];
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
    if (!Number.isSafeInteger(quantity) || quantity < 1 ||
        typeof product.id !== 'string' || product.id.trim().length === 0 ||
        !Number.isFinite(product.markedPrice) || product.markedPrice < 0 ||
        !Number.isSafeInteger(product.stockQuantity) || product.stockQuantity < 1) {
      reportWarning('cart');
      return of(undefined);
    }
    const available = this.availableStock(product);
    if (available < 1) {
      reportWarning('cart');
      return of(undefined);
    }
    const acceptedQuantity = Math.min(quantity, available);
    if (acceptedQuantity < quantity) {
      reportWarning('cart');
    }

    // An uploaded image keeps its own line, but every variant shares the same product stock.
    if (image) {
      this.cartItems.push({
        cartItemId: this.generateUniqueCartItemId(), product, quantity: acceptedQuantity, goldBorder, image
      });
    } else {
      const existingItem = this.cartItems.find(item =>
        item.product.id === product.id && item.goldBorder === goldBorder && !item.image && !item.customImageUploadId && !item.requiresArtworkReselection
      );
      if (existingItem) {
        existingItem.quantity += acceptedQuantity;
      } else {
        this.cartItems.push({
          cartItemId: this.generateUniqueCartItemId(), product, quantity: acceptedQuantity, goldBorder
        });
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

  invalidateArtwork(uploadId: string): void {
    for (const item of this.cartItems) {
      if (item.customImageUploadId !== uploadId) continue;
      delete item.customImageUploadId;
      item.requiresArtworkReselection = !item.image;
    }
    this.updateCart();
  }

  reselectArtwork(cartItemId: string, file: File): void {
    if (!file.type.startsWith('image/') || file.size > 5_000_000 || file.size === 0) return;
    const item: CartItem | undefined = this.cartItems.find((candidate: CartItem) => candidate.cartItemId === cartItemId);
    if (!item) return;
    item.image = file;
    delete item.customImageUploadId;
    item.requiresArtworkReselection = false;
    this.updateCart();
  }

  private serializableItems(items: CartItem[]): Omit<CartItem, 'image'>[] {
    return items.map(({image, ...item}: CartItem) => ({...item,
      requiresArtworkReselection: item.requiresArtworkReselection || (!!image && !item.customImageUploadId)}));
  }

  removeFromCart(cartItemId: string): Observable<void> {
    this.cartItems = this.cartItems.filter(item => item.cartItemId !== cartItemId);
    this.updateCart();
    return of(undefined);
  }

  updateItemQuantity(cartItemId: string, quantity: number): Observable<void> {
    const itemIndex = this.cartItems.findIndex(item => item.cartItemId === cartItemId);
    if (itemIndex < 0 || !Number.isSafeInteger(quantity)) {
      reportWarning('cart');
      return of(undefined);
    }
    const item = this.cartItems[itemIndex];
    if (!Number.isSafeInteger(item.product.stockQuantity) || item.product.stockQuantity < 1) {
      reportWarning('cart');
      return of(undefined);
    }
    const available = this.availableStock(item.product, cartItemId);
    if (available < 1) {
      reportWarning('cart');
      return of(undefined);
    }
    // Removal is removeFromCart's job; a zero update still floors at one.
    item.quantity = Math.max(1, Math.min(quantity, available));
    this.updateCart();
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

  private availableStock(product: Product, excludedCartItemId?: string): number {
    const variants = this.cartItems.filter(item => item.product.id === product.id);
    const stockLimit = variants.reduce(
      (limit, item) => Math.min(limit, item.product.stockQuantity), product.stockQuantity
    );
    const reserved = variants.reduce(
      (total, item) => total + (item.cartItemId === excludedCartItemId ? 0 : item.quantity), 0
    );
    return Math.max(0, stockLimit - reserved);
  }

  rememberPurchase(orderId: string, customerId: string, lines: PurchasedCartLine[]): void {
    if (this.purchases.some((purchase: CartPurchase) => purchase.orderId === orderId)) return;
    const purchases: CartPurchase[] = [...this.purchases,
      {orderId, customerId, lines: lines.map((line: PurchasedCartLine) => ({...line})), completed: false}];
    localStorage.setItem(this.localStorageKey, JSON.stringify({
      version: 1, items: this.serializableItems(this.cartItems), purchases}));
    this.purchases = purchases;
  }

  completePurchase(orderId: string, customerId: string): void {
    const purchase: CartPurchase | undefined = this.purchases.find((entry: CartPurchase) =>
      entry.orderId === orderId && entry.customerId === customerId);
    if (!purchase || purchase.completed) return;
    const updated: CartItem[] = this.cartItems.map((item: CartItem) => {
      const purchased: PurchasedCartLine | undefined = purchase.lines.find((line: PurchasedCartLine) =>
        line.cartItemId === item.cartItemId);
      return {...item, quantity: Math.max(0, item.quantity - (purchased?.quantity ?? 0))};
    }).filter((item: CartItem) => item.quantity > 0);
    const purchases: CartPurchase[] = this.purchases.map((entry: CartPurchase) =>
      entry === purchase ? {...entry, completed: true} : entry);
    // Persist the deduction and its receipt in one write before publishing either.
    localStorage.setItem(this.localStorageKey, JSON.stringify({version: 1, items: this.serializableItems(updated), purchases}));
    this.cartItems = updated;
    this.purchases = purchases;
    this.cartItemsSubject.next([...updated]);
    this.calculateAndEmitTotal();
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
      const serializableCart = this.serializableItems(this.cartItems);
      localStorage.setItem(this.localStorageKey, JSON.stringify({version: 1, items: serializableCart, purchases: this.purchases}));
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
        const envelope = parsed as {version?: unknown; items?: unknown; purchases?: unknown};
        const restored: CartItem[] = this.sanitizeRestoredCart(Array.isArray(parsed) ? parsed : envelope?.version === 1 ? envelope.items : []);
        if (!Array.isArray(parsed) && envelope?.version === 1 && Array.isArray(envelope.purchases)) {
          this.purchases = envelope.purchases.filter((entry: unknown): entry is CartPurchase => {
            const purchase = entry as Partial<CartPurchase> | null;
            return !!purchase && typeof purchase.orderId === 'string' && typeof purchase.customerId === 'string'
              && typeof purchase.completed === 'boolean' && Array.isArray(purchase.lines)
              && purchase.lines.every((line: PurchasedCartLine) => !!line && typeof line.cartItemId === 'string'
                && Number.isSafeInteger(line.quantity) && line.quantity > 0);
          });
        }
        this.cartItems = restored;
        this.cartItemsSubject.next([...this.cartItems]);
        this.calculateAndEmitTotal(); // Calculate total after loading
      }
    } catch (e) {
      reportError('storage', e);
      this.cartItems = [];
      try {
        localStorage.removeItem(this.localStorageKey);
      } catch (storageError) {
        reportError('storage', storageError);
      }
      this.cartItemsSubject.next([]);
      this.calculateAndEmitTotal(); // Emit 0 total
    }
  }

  private sanitizeRestoredCart(parsed: unknown): CartItem[] {
    if (!Array.isArray(parsed)) {
      return [];
    }
    const lines = parsed.filter((entry): entry is CartItem & {product: Product & {id: string}} =>
      this.isRestorableCartLine(entry)
    );
    const stockLimits = new Map<string, number>();
    for (const line of lines) {
      stockLimits.set(line.product.id, Math.min(
        stockLimits.get(line.product.id) ?? line.product.stockQuantity, line.product.stockQuantity
      ));
    }
    const seenIds = new Set<string>();
    const reservedByProduct = new Map<string, number>();
    const valid: CartItem[] = [];
    for (const line of lines) {
      const reserved = reservedByProduct.get(line.product.id) ?? 0;
      const available = (stockLimits.get(line.product.id) ?? 0) - reserved;
      if (available < 1) {
        continue;
      }
      line.quantity = Math.min(line.quantity, available);
      reservedByProduct.set(line.product.id, reserved + line.quantity);
      if (seenIds.has(line.cartItemId)) {
        // Duplicate identity: mint a fresh id so keyed ops stay 1:1.
        line.cartItemId = this.generateUniqueCartItemId();
      }
      seenIds.add(line.cartItemId);
      valid.push(line);
    }
    return valid;
  }

  private readPersistedItems(parsed: unknown): unknown {
    if (Array.isArray(parsed)) {
      // Accept the pre-versioned format for one migration cycle.
      return parsed;
    }
    if (typeof parsed !== 'object' || parsed === null) {
      return [];
    }
    const envelope = parsed as {version?: unknown; items?: unknown};
    return envelope.version === CartService.storageVersion ? envelope.items : [];
  }

  private isRestorableCartLine(entry: unknown): entry is CartItem & {product: Product & {id: string}} {
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
    if (typeof line.quantity !== 'number' || !Number.isSafeInteger(line.quantity) || line.quantity < 1) {
      return false;
    }
    if (typeof line.product.markedPrice !== 'number' || !Number.isFinite(line.product.markedPrice) || line.product.markedPrice < 0) {
      return false;
    }
    if (typeof line.product.stockQuantity !== 'number' || !Number.isSafeInteger(line.product.stockQuantity) || line.product.stockQuantity < 1) {
      return false;
    }
    if (line.requiresArtworkReselection !== undefined && typeof line.requiresArtworkReselection !== 'boolean') return false;
    if (line.customImageUploadId !== undefined
      && (typeof line.customImageUploadId !== 'string' || line.customImageUploadId.trim().length === 0)) {
      return false;
    }
    return true;
  }
}
