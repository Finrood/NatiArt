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
  private readonly maxProductQuantity = 100;
  private static readonly storageVersion = 1;
  private cartItems: CartItem[] = [];
  private purchases: CartPurchase[] = [];
  private mergedGuestTransfers: string[] = [];
  private lastStorage: string | null = null;
  private localStorageKey = 'natiart-cart';
  private cartItemsSubject = new BehaviorSubject<CartItem[]>([]);
  private cartTotalSubject = new BehaviorSubject<number>(0);

  constructor() {
    this.loadCartFromLocalStorage();
    this.calculateAndEmitTotal();
  }

  /** Account baskets are isolated; a guest transfer is journalled before its source is removed. */
  useAccount(accountId: string | null): void {
    const nextKey: string = accountId ? 'natiart-cart:account:' + encodeURIComponent(accountId) : 'natiart-cart';
    if (nextKey === this.localStorageKey) return;
    try {
      this.synchronizeBasket();
      const fromGuest: boolean = this.localStorageKey === 'natiart-cart' && !!accountId;
      const guestItems: CartItem[] = fromGuest ? this.cartItems.map((item: CartItem): CartItem => ({...item})) : [];
      const guestPurchases: CartPurchase[] = fromGuest ? this.purchases : [];
      let transferId: string = '';
      if (fromGuest && (guestItems.length || guestPurchases.length)) {
        const raw: string | null = localStorage.getItem('natiart-cart');
        const source: {transferId?: string} = raw ? JSON.parse(raw) : {};
        transferId = source.transferId ?? crypto.randomUUID();
        localStorage.setItem('natiart-cart', JSON.stringify({version: 1, items: this.serializableItems(guestItems),
          purchases: guestPurchases, transferId}));
      }
      this.localStorageKey = nextKey;
      this.cartItems = []; this.purchases = []; this.mergedGuestTransfers = [];
      this.loadCartFromLocalStorage(true);
      if (fromGuest && transferId && !this.mergedGuestTransfers.includes(transferId)) {
        const aliases: Map<string, string> = new Map<string, string>();
        const pendingLines: Set<string> = new Set([...this.purchases, ...guestPurchases].filter((purchase: CartPurchase): boolean => !purchase.completed)
          .flatMap((purchase: CartPurchase): string[] => purchase.lines.map((line: PurchasedCartLine): string => line.cartItemId)));
        // Pending purchases retain separate line identity through the transfer.
        for (const line of guestItems) {
          const originalId: string = line.cartItemId;
          if (this.cartItems.some((item: CartItem): boolean => item.cartItemId === originalId)) {
            line.cartItemId = this.generateUniqueCartItemId();
            if (pendingLines.has(originalId)) pendingLines.add(line.cartItemId);
          }
          if (line.customImageUploadId) {
            delete line.customImageUploadId;
            line.requiresArtworkReselection = !line.image;
          }
          const plain: boolean = !pendingLines.has(line.cartItemId) && !line.image && !line.customImageUploadId && !line.requiresArtworkReselection;
          const match: CartItem | undefined = plain ? this.cartItems.find((item: CartItem): boolean =>
            !pendingLines.has(item.cartItemId) && item.product.id === line.product.id && item.goldBorder === line.goldBorder && !item.image && !item.customImageUploadId && !item.requiresArtworkReselection) : undefined;
          if (match) { match.quantity += line.quantity; aliases.set(originalId, match.cartItemId); }
          else { this.cartItems.push(line); aliases.set(originalId, line.cartItemId); }
        }
        this.cartItems.sort((a: CartItem, b: CartItem): number => Number(pendingLines.has(b.cartItemId)) - Number(pendingLines.has(a.cartItemId)));
        this.cartItems = this.sanitizeRestoredCart(this.cartItems);
        for (const purchase of guestPurchases) {
          const previous: CartPurchase | undefined = this.purchases.find((entry: CartPurchase): boolean => entry.orderId === purchase.orderId);
          if (!previous) {
            this.purchases.push({...purchase, lines: purchase.lines.map((line: PurchasedCartLine): PurchasedCartLine =>
              ({...line, cartItemId: aliases.get(line.cartItemId) ?? line.cartItemId}))});
          } else if (purchase.completed && !previous.completed) {
            // A different tab may have confirmed payment after this basket was transferred.
            this.cartItems = this.cartItems.map((item: CartItem): CartItem => ({...item,
              quantity: Math.max(0, item.quantity - (previous.lines.find((line: PurchasedCartLine): boolean => line.cartItemId === item.cartItemId)?.quantity ?? 0))
            })).filter((item: CartItem): boolean => item.quantity > 0);
            previous.completed = true;
          }
        }
        this.mergedGuestTransfers = [...this.mergedGuestTransfers, transferId].slice(-100);
        const raw: string = JSON.stringify({version: 1, items: this.serializableItems(this.cartItems),
          purchases: this.purchases, mergedGuestTransfers: this.mergedGuestTransfers});
        localStorage.setItem(nextKey, raw);
        this.lastStorage = raw;
      }
      if (fromGuest && transferId) localStorage.removeItem('natiart-cart');
      this.cartItemsSubject.next([...this.cartItems]); this.calculateAndEmitTotal();
    } catch (error) {
      this.localStorageKey = nextKey;
      this.cartItems = []; this.purchases = []; this.mergedGuestTransfers = [];
      this.cartItemsSubject.next([]); this.calculateAndEmitTotal();
      throw error;
    }
  }

  /** Call only after the server confirms a payment for an authorized order. */
  completeVerifiedPurchase(orderId: string): void {
    const purchase: CartPurchase | undefined = this.purchases.find((entry: CartPurchase): boolean => entry.orderId === orderId);
    if (purchase) this.completePurchase(orderId, purchase.customerId);
  }

  /** Refresh this basket before a stale tab can overwrite a newer selection or receipt. */
  private synchronizeBasket(strict: boolean = false): void {
    let current: string | null;
    try { current = localStorage.getItem(this.localStorageKey); }
    catch (error) { if (strict) throw error; reportError('storage', error); return; }
    if (current === this.lastStorage) return;
    this.cartItems = []; this.purchases = []; this.mergedGuestTransfers = [];
    this.loadCartFromLocalStorage(true);
    this.cartItemsSubject.next([...this.cartItems]); this.calculateAndEmitTotal();
  }

  getCartItems(): Observable<CartItem[]> {
    return this.cartItemsSubject.asObservable();
  }

  getCartTotalSnapshot(): number {
    return this.cartTotalSubject.value;
  }

  addToCart(product: Product, quantity: number, goldBorder?: boolean, image?: File): Observable<void> {
    this.synchronizeBasket();
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
    this.synchronizeBasket();
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
    this.synchronizeBasket();
    for (const item of this.cartItems) {
      if (item.customImageUploadId !== uploadId) continue;
      delete item.customImageUploadId;
      item.requiresArtworkReselection = !item.image;
    }
    this.updateCart();
  }

  reselectArtwork(cartItemId: string, file: File): void {
    this.synchronizeBasket();
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
    this.synchronizeBasket();
    this.cartItems = this.cartItems.filter(item => item.cartItemId !== cartItemId);
    this.updateCart();
    return of(undefined);
  }

  updateItemQuantity(cartItemId: string, quantity: number): Observable<void> {
    this.synchronizeBasket();
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
    this.synchronizeBasket();
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
    return Math.max(0, Math.min(this.maxProductQuantity, stockLimit) - reserved);
  }

  rememberPurchase(orderId: string, customerId: string, lines: PurchasedCartLine[]): void {
    this.synchronizeBasket(true);
    if (this.purchases.some((purchase: CartPurchase) => purchase.orderId === orderId)) return;
    const purchases: CartPurchase[] = [...this.purchases,
      {orderId, customerId, lines: lines.map((line: PurchasedCartLine) => ({...line})), completed: false}];
    const raw: string = JSON.stringify({
      version: 1, items: this.serializableItems(this.cartItems), purchases, mergedGuestTransfers: this.mergedGuestTransfers});
    localStorage.setItem(this.localStorageKey, raw);
    this.lastStorage = raw;
    this.purchases = purchases;
  }

  completePurchase(orderId: string, customerId: string): void {
    const remembered: CartPurchase | undefined = this.purchases.find((entry: CartPurchase): boolean =>
      entry.orderId === orderId && entry.customerId === customerId);
    this.synchronizeBasket(true);
    const purchase: CartPurchase | undefined = this.purchases.find((entry: CartPurchase) =>
      entry.orderId === orderId && entry.customerId === customerId) ?? remembered;
    if (!purchase || purchase.completed) return;
    const updated: CartItem[] = this.cartItems.map((item: CartItem) => {
      const purchased: PurchasedCartLine | undefined = purchase.lines.find((line: PurchasedCartLine) =>
        line.cartItemId === item.cartItemId);
      return {...item, quantity: Math.max(0, item.quantity - (purchased?.quantity ?? 0))};
    }).filter((item: CartItem) => item.quantity > 0);
    // A sign-in in another tab may have transferred the source receipt. Keep its
    // completion for the transfer journal without restoring its old cart items.
    const purchases: CartPurchase[] = this.purchases.includes(purchase)
      ? this.purchases.map((entry: CartPurchase): CartPurchase => entry === purchase ? {...entry, completed: true} : entry)
      : [...this.purchases, {...purchase, completed: true}];
    // Persist the deduction and its receipt in one write before publishing either.
    const raw: string = JSON.stringify({version: 1, items: this.serializableItems(updated), purchases, mergedGuestTransfers: this.mergedGuestTransfers});
    localStorage.setItem(this.localStorageKey, raw);
    this.cartItems = updated;
    this.lastStorage = raw;
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
      const raw: string = JSON.stringify({version: 1, items: serializableCart, purchases: this.purchases, mergedGuestTransfers: this.mergedGuestTransfers});
      localStorage.setItem(this.localStorageKey, raw);
      this.lastStorage = raw;
    } catch (e) {
      reportError('storage', e);
    }
  }

  private loadCartFromLocalStorage(strict: boolean = false): void {
    try {
      const savedCart = localStorage.getItem(this.localStorageKey);
      this.lastStorage = savedCart;
      if (savedCart) {
        const parsed: unknown = JSON.parse(savedCart);
        // AS1: drop corrupt-but-parseable shape before emit — a missing or
        // duplicate cartItemId collapses map keys so remove/quantity ops hit
        // every line at once or none, and a null product NPEs the total.
        const envelope = parsed as {version?: unknown; items?: unknown; purchases?: unknown; mergedGuestTransfers?: unknown};
        this.mergedGuestTransfers = Array.isArray(envelope?.mergedGuestTransfers)
          ? envelope.mergedGuestTransfers.filter((value: unknown): value is string => typeof value === "string").slice(-100) : [];
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
      if (strict) throw e;
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
        stockLimits.get(line.product.id) ?? Math.min(line.product.stockQuantity, this.maxProductQuantity), Math.min(line.product.stockQuantity, this.maxProductQuantity)
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
