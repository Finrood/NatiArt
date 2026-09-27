import {TestBed} from '@angular/core/testing';

import {CartService} from './cart.service';
import {CartItem} from '../models/CartItem.model';
import {Product} from '../models/product.model';

describe('CartService', () => {
  let service: CartService;

  const product = (overrides: Partial<Product> = {}): Product => ({
    id: 'p1',
    label: 'Painting',
    originalPrice: 100,
    markedPrice: 80,
    stockQuantity: 5,
    categoryId: 'cat-1',
    availablePersonalizations: [],
    tags: new Set<string>(),
    images: [],
    ...overrides
  });

  beforeEach(() => {
    localStorage.removeItem('natiart-cart');
    TestBed.configureTestingModule({});
    service = TestBed.inject(CartService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('addToCart_clampsOverStockQuantityToAvailableStock', () => {
    service.addToCart(product({stockQuantity: 2}), 10).subscribe();

    const items: CartItem[] = service.getCartItemsSnapshot();
    expect(items.length).toBe(1);
    expect(items[0].quantity).toBe(2);
  });

  it('addToCart_groupsIdenticalLinesAndCapsTheCombinedQuantityAtStock', () => {
    service.addToCart(product(), 3).subscribe();
    service.addToCart(product(), 4).subscribe();

    const items: CartItem[] = service.getCartItemsSnapshot();
    expect(items.length).toBe(1);
    expect(items[0].quantity).toBe(5);
  });

  it('rejects nonfinite and fractional quantities before changing cart totals', () => {
    for (const invalid of [NaN, Infinity, 1.5]) {
      service.addToCart(product(), invalid).subscribe();
    }
    expect(service.getCartItemsSnapshot()).toEqual([]);
    expect(service.getCartTotalSnapshot()).toBe(0);

    service.addToCart(product(), 2).subscribe();
    const cartItemId = service.getCartItemsSnapshot()[0].cartItemId;
    for (const invalid of [NaN, Infinity, 2.5]) {
      service.updateItemQuantity(cartItemId, invalid).subscribe();
    }
    expect(service.getCartItemsSnapshot()[0].quantity).toBe(2);
    expect(service.getCartTotalSnapshot()).toBe(160);
  });

  it('caps all product variants together including custom images and updates', () => {
    service.addToCart(product(), 3, false).subscribe();
    service.addToCart(product(), 4, true).subscribe();
    service.addToCart(product(), 1, false, new File([], 'custom.png')).subscribe();

    const items = service.getCartItemsSnapshot();
    expect(items.map(item => item.quantity)).toEqual([3, 2]);
    expect(items.reduce((sum, item) => sum + item.quantity, 0)).toBe(5);

    service.updateItemQuantity(items[0].cartItemId, 5).subscribe();
    expect(service.getCartItemsSnapshot().map(item => item.quantity)).toEqual([3, 2]);

    service.updateItemQuantity(items[1].cartItemId, 1).subscribe();
    service.updateItemQuantity(items[0].cartItemId, 5).subscribe();
    expect(service.getCartItemsSnapshot().map(item => item.quantity)).toEqual([4, 1]);
    expect(service.getCartTotalSnapshot()).toBe(400);
  });

  it('clamps restored variants to their shared product stock', () => {
    localStorage.setItem('natiart-cart', JSON.stringify({version: 1, items: [
      {cartItemId: 'plain', product: product(), quantity: 4, goldBorder: false},
      {cartItemId: 'border', product: product(), quantity: 4, goldBorder: true},
    ]}));

    const restored = new CartService();

    expect(restored.getCartItemsSnapshot().map(item => item.quantity)).toEqual([4, 1]);
    expect(restored.getCartTotalSnapshot()).toBe(400);
  });

  it('getCartTotal_emitsTheSumOfMarkedPriceTimesQuantity', () => {
    let total: number | undefined;
    service.getCartTotal().subscribe(value => total = value);

    service.addToCart(product({id: 'a', markedPrice: 10}), 2).subscribe();
    service.addToCart(product({id: 'b', markedPrice: 7.5}), 3).subscribe();

    expect(total).toBe(42.5);
  });

  it('updateItemQuantity_clampsToStockAndFloorsAtOne', () => {
    service.addToCart(product(), 1).subscribe();
    const cartItemId = service.getCartItemsSnapshot()[0].cartItemId;

    service.updateItemQuantity(cartItemId, 99).subscribe();
    expect(service.getCartItemsSnapshot()[0].quantity).toBe(5);

    service.updateItemQuantity(cartItemId, 0).subscribe();
    expect(service.getCartItemsSnapshot()[0].quantity).toBe(1);
  });

  it('savesAnImageFreeCartToLocalStorageAndRestoresItInANewInstance', () => {
    service.addToCart(product(), 2).subscribe();
    const saved = localStorage.getItem('natiart-cart');
    expect(saved).toContain('"p1"');

    const restored = new CartService();
    const items: CartItem[] = restored.getCartItemsSnapshot();
    expect(items.length).toBe(1);
    expect(items[0].product.id).toBe('p1');
    expect(restored.getCartTotalSnapshot()).toBe(160);
  });

  it('persistsOrdinaryLinesWhenACustomImageLineCannotBeRestored', () => {
    service.addToCart(product(), 1, false, new File([], 'art.png')).subscribe();
    service.addToCart(product({id: 'p2'}), 2).subscribe();

    const restored = new CartService();
    expect(restored.getCartItemsSnapshot().map(item => item.product.id)).toEqual(['p2']);
  });

  it('recoversToAnEmptyCartWhenThePersistedCartIsCorrupt', () => {
    localStorage.setItem('natiart-cart', 'not-json{');

    const restored = new CartService();

    expect(restored.getCartItemsSnapshot()).toEqual([]);
    expect(restored.getCartTotalSnapshot()).toBe(0);
    expect(localStorage.getItem('natiart-cart')).toBeNull();
  });

  it('restoresOnlyWellFormedLinesWhenPersistedIdentityIsTampered (AS1)', () => {
    const validLine = {cartItemId: 'keep-1', product: product({id: 'p1'}), quantity: 2};
    localStorage.setItem('natiart-cart', JSON.stringify([
      validLine,
      {cartItemId: '', product: product({id: 'p2'}), quantity: 1},
      {cartItemId: 'no-product', product: null, quantity: 1},
      {cartItemId: 'no-quantity', product: product({id: 'p3'}), quantity: 0},
    ]));

    const restored = new CartService();
    const items: CartItem[] = restored.getCartItemsSnapshot();

    expect(items.length).toBe(1);
    expect(items[0].cartItemId).toBe('keep-1');
    expect(items[0].product.id).toBe('p1');
    expect(restored.getCartTotalSnapshot()).toBe(160);
  });

  it('regeneratesCollidingRestoredIdsSoKeyedOpsStayOneToOne (AS1)', () => {
    localStorage.setItem('natiart-cart', JSON.stringify([
      {cartItemId: 'dup', product: product({id: 'p1'}), quantity: 1},
      {cartItemId: 'dup', product: product({id: 'p2'}), quantity: 1},
    ]));

    const restored = new CartService();
    const items: CartItem[] = restored.getCartItemsSnapshot();

    expect(items.length).toBe(2);
    expect(items[0].cartItemId).not.toBe(items[1].cartItemId);

    restored.removeFromCart(items[0].cartItemId).subscribe();
    expect(restored.getCartItemsSnapshot().length).toBe(1);
  });

  it('updateItemQuantity_floorsAtOneAndLeavesRemovalToRemoveFromCart (BW1)', () => {
    service.addToCart(product(), 1).subscribe();
    const cartItemId = service.getCartItemsSnapshot()[0].cartItemId;

    service.updateItemQuantity(cartItemId, 0).subscribe();
    expect(service.getCartItemsSnapshot().length).toBe(1);
    expect(service.getCartItemsSnapshot()[0].quantity).toBe(1);

    service.removeFromCart(cartItemId).subscribe();
    expect(service.getCartItemsSnapshot()).toEqual([]);
  });
});
