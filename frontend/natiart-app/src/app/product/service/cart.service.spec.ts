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
    tags: [],
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

  it('caps combined personalized variants at the server per-product quantity', () => {
    const stocked = product({stockQuantity: 200});
    service.addToCart(stocked, 75, false).subscribe();
    service.addToCart(stocked, 50, true).subscribe();

    const items = service.getCartItemsSnapshot();
    expect(items.map(item => item.quantity)).toEqual([75, 25]);
    service.updateItemQuantity(items[0].cartItemId, 100).subscribe();
    expect(service.getCartItemsSnapshot().reduce((total, item) => total + item.quantity, 0)).toBe(100);
  });

  it('caps a restored cart that predates the quantity boundary', () => {
    const stocked = product({stockQuantity: 200});
    localStorage.setItem('natiart-cart', JSON.stringify([
      {cartItemId: 'one', product: stocked, quantity: 75, goldBorder: false},
      {cartItemId: 'two', product: stocked, quantity: 75, goldBorder: true},
    ]));

    const restored = new CartService();

    expect(restored.getCartItemsSnapshot().map(item => item.quantity)).toEqual([75, 25]);
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

  it('keepsPersonalizationVariantsAsSeparateStableLines', () => {
    service.addToCart(product(), 1, true).subscribe();
    service.addToCart(product(), 1, false, new File([], 'art.png')).subscribe();

    const items = service.getCartItemsSnapshot();
    expect(items.length).toBe(2);
    expect(items[0].cartItemId).not.toBe(items[1].cartItemId);

    service.setCustomImageUploadId(items[1].cartItemId, 'upload-1').subscribe();
    expect(service.getCartItemsSnapshot()[1].customImageUploadId).toBe('upload-1');
    expect(JSON.parse(localStorage.getItem('natiart-cart') ?? '{}').items).toEqual([
      jasmine.objectContaining({goldBorder: true}),
      jasmine.objectContaining({customImageUploadId: 'upload-1'}),
    ]);
  });

  it('preservesOrdinaryLinesWhenAnArtworkDraftCannotBeSerialized', () => {
    service.addToCart(product(), 1).subscribe();
    service.addToCart(product({id: 'p2'}), 1, false, new File([], 'art.png')).subscribe();

    const restored = new CartService();
    expect(restored.getCartItemsSnapshot().map(item => item.product.id)).toEqual(['p1', 'p2']);
    expect(restored.getCartItemsSnapshot()[1].requiresArtworkReselection).toBeTrue();
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
    expect(restored.getCartItemsSnapshot().map(item => item.product.id)).toEqual(['p1', 'p2']);
    expect(restored.getCartItemsSnapshot()[0].requiresArtworkReselection).toBeTrue();
    expect(JSON.parse(localStorage.getItem('natiart-cart')!).items.length).toBe(2);
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
  it('deducts purchased quantities once across reloads and preserves additions and other variants', () => {
    service.addToCart(product(), 2).subscribe();
    const line: CartItem = service.getCartItemsSnapshot()[0];
    service.rememberPurchase('ord-1', 'cus_MINE', [{cartItemId: line.cartItemId, quantity: 2}]);
    service.addToCart(product(), 2).subscribe();
    service.addToCart(product(), 1, true).subscribe();
    service.addToCart(product({id: 'other'}), 1).subscribe();
    service.completePurchase('ord-1', 'foreign');
    expect(service.getCartItemsSnapshot()[0].quantity).toBe(4);
    service.completePurchase('ord-1', 'cus_MINE');
    expect(service.getCartItemsSnapshot().map((item: CartItem) => item.quantity)).toEqual([2, 1, 1]);
    const restored: CartService = new CartService();
    restored.completePurchase('ord-1', 'cus_MINE');
    expect(restored.getCartItemsSnapshot().map((item: CartItem) => item.quantity)).toEqual([2, 1, 1]);
  });

  it('keeps cart and receipt unchanged when a completion cannot be persisted', () => {
    service.addToCart(product(), 2).subscribe();
    const line: CartItem = service.getCartItemsSnapshot()[0];
    service.rememberPurchase('ord-1', 'cus_MINE', [{cartItemId: line.cartItemId, quantity: 2}]);
    const storage = spyOn(localStorage, 'setItem').and.throwError('full');
    expect(() => service.completePurchase('ord-1', 'cus_MINE')).toThrow();
    expect(service.getCartItemsSnapshot()[0].quantity).toBe(2);
    storage.and.callThrough();
    service.completePurchase('ord-1', 'cus_MINE');
    expect(service.getCartItemsSnapshot()).toEqual([]);
  });

  it('isolates account baskets and merges guest quantities exactly once', () => {
    localStorage.removeItem('natiart-cart:account:account-a'); localStorage.removeItem('natiart-cart:account:account-b');
    service.useAccount('account-a'); service.addToCart(product({stockQuantity: 10}), 2).subscribe();
    service.useAccount(null); expect(service.getCartItemsSnapshot()).toEqual([]);
    service.addToCart(product({stockQuantity: 10}), 3).subscribe(); service.useAccount('account-a');
    expect(service.getCartItemsSnapshot()[0].quantity).toBe(5);
    service.useAccount('account-b'); expect(service.getCartItemsSnapshot()).toEqual([]);
    service.useAccount('account-a'); expect(service.getCartItemsSnapshot()[0].quantity).toBe(5);
    expect(localStorage.getItem('natiart-cart')).toBeNull();
    localStorage.removeItem('natiart-cart:account:account-a'); localStorage.removeItem('natiart-cart:account:account-b');
  });
  it('replays a partially completed merge without doubling the guest basket', () => {
    localStorage.removeItem('natiart-cart:account:merge-account');
    service.addToCart(product({stockQuantity: 10}), 2).subscribe();
    const source: string = localStorage.getItem('natiart-cart')!;
    const remove: jasmine.Spy = spyOn(localStorage, 'removeItem').and.throwError('storage busy');
    expect(() => service.useAccount('merge-account')).toThrow();
    const journal: string = localStorage.getItem('natiart-cart')!;
    expect(JSON.parse(journal).transferId).toBeTruthy();
    const replay: CartService = new CartService(); remove.and.callThrough(); replay.useAccount('merge-account');
    expect(replay.getCartItemsSnapshot()[0].quantity).toBe(2);
    expect(localStorage.getItem('natiart-cart')).toBeNull();
    localStorage.removeItem('natiart-cart:account:merge-account');
  });
  it('preserves account artwork and clears only guest upload ownership during a merge', () => {
    localStorage.removeItem('natiart-cart:account:art-account');
    service.useAccount('art-account'); service.addToCart(product({stockQuantity: 10}), 1).subscribe();
    service.setCustomImageUploadId(service.getCartItemsSnapshot()[0].cartItemId, 'account-artwork').subscribe();
    service.useAccount(null); service.addToCart(product({stockQuantity: 10}), 1).subscribe();
    service.setCustomImageUploadId(service.getCartItemsSnapshot()[0].cartItemId, 'guest-artwork').subscribe();
    service.useAccount('art-account');
    expect(service.getCartItemsSnapshot().find(item => item.customImageUploadId === 'account-artwork')).toBeTruthy();
    expect(service.getCartItemsSnapshot().find(item => item.requiresArtworkReselection)).toBeTruthy();
    expect(service.getCartItemsSnapshot().some(item => item.customImageUploadId === 'guest-artwork')).toBeFalse();
    localStorage.removeItem('natiart-cart:account:art-account');
  });
  it('keeps a pending guest purchase separate and removes only its purchased units after sign-in', () => {
    localStorage.removeItem('natiart-cart:account:paid-account');
    service.useAccount('paid-account'); service.addToCart(product({stockQuantity: 10}), 2).subscribe();
    service.useAccount(null); service.addToCart(product({stockQuantity: 10}), 3).subscribe();
    service.rememberPurchase('guest-order', 'cus_guest', [{cartItemId: service.getCartItemsSnapshot()[0].cartItemId, quantity: 3}]);
    service.useAccount('paid-account'); expect(service.getCartItemsSnapshot().length).toBe(2);
    service.completeVerifiedPurchase('guest-order'); expect(service.getCartItemsSnapshot()[0].quantity).toBe(2);
    service.completeVerifiedPurchase('guest-order'); expect(service.getCartItemsSnapshot()[0].quantity).toBe(2);
    localStorage.removeItem('natiart-cart:account:paid-account');
  });

  it('does not transfer a stale guest basket twice from two open tabs', () => {
    localStorage.removeItem('natiart-cart:account:two-tabs');
    service.addToCart(product({stockQuantity: 10}), 2).subscribe();
    const otherTab: CartService = new CartService();
    service.useAccount('two-tabs'); otherTab.useAccount('two-tabs');
    expect(otherTab.getCartItemsSnapshot()[0].quantity).toBe(2);
    localStorage.removeItem('natiart-cart:account:two-tabs');
  });
  it('adds new guest selections after another tab consumed the earlier guest basket', () => {
    localStorage.removeItem('natiart-cart:account:two-tabs-new');
    service.addToCart(product({stockQuantity: 10}), 2).subscribe();
    const otherTab: CartService = new CartService(); service.useAccount('two-tabs-new');
    otherTab.addToCart(product({stockQuantity: 10}), 1).subscribe(); otherTab.useAccount('two-tabs-new');
    expect(otherTab.getCartItemsSnapshot()[0].quantity).toBe(3);
    localStorage.removeItem('natiart-cart:account:two-tabs-new');
  });

  it('settles a transferred purchase when a different tab confirmed its payment', () => {
    localStorage.removeItem('natiart-cart:account:paid-other-tab');
    service.addToCart(product({stockQuantity: 10}), 2).subscribe();
    service.rememberPurchase('paid-elsewhere', 'cus_guest', [{cartItemId: service.getCartItemsSnapshot()[0].cartItemId, quantity: 2}]);
    const signedIn: CartService = new CartService(); signedIn.useAccount('paid-other-tab');
    service.completeVerifiedPurchase('paid-elsewhere'); signedIn.useAccount(null); signedIn.useAccount('paid-other-tab');
    expect(signedIn.getCartItemsSnapshot()).toEqual([]);
    localStorage.removeItem('natiart-cart:account:paid-other-tab');
  });

  it('preserves guest additions from another tab when an earlier payment completes', (): void => {
    service.addToCart(product({stockQuantity: 10}), 2).subscribe();
    service.rememberPurchase('guest-cross-tab', 'cus_guest', [{cartItemId: service.getCartItemsSnapshot()[0].cartItemId, quantity: 2}]);
    const otherTab: CartService = new CartService();
    otherTab.addToCart(product({stockQuantity: 10}), 3).subscribe();
    otherTab.addToCart(product({id: 'later-piece'}), 1).subscribe();
    service.completeVerifiedPurchase('guest-cross-tab');
    const restored: CartService = new CartService();
    expect(restored.getCartItemsSnapshot().map((item: CartItem): number => item.quantity)).toEqual([3, 1]);
    otherTab.completeVerifiedPurchase('guest-cross-tab');
    expect(new CartService().getCartItemsSnapshot().map((item: CartItem): number => item.quantity)).toEqual([3, 1]);
  });

  it('preserves account additions and settles a purchase only once across payment tabs', (): void => {
    const key: string = 'natiart-cart:account:account-payment-tabs';
    localStorage.removeItem(key);
    try {
      service.useAccount('account-payment-tabs'); service.addToCart(product({stockQuantity: 10}), 2).subscribe();
      service.rememberPurchase('account-cross-tab', 'cus_account', [{cartItemId: service.getCartItemsSnapshot()[0].cartItemId, quantity: 2}]);
      const otherTab: CartService = new CartService(); otherTab.useAccount('account-payment-tabs');
      otherTab.addToCart(product({stockQuantity: 10}), 3).subscribe();
      service.completeVerifiedPurchase('account-cross-tab');
      otherTab.completeVerifiedPurchase('account-cross-tab');
      const restored: CartService = new CartService(); restored.useAccount('account-payment-tabs');
      expect(restored.getCartItemsSnapshot().map((item: CartItem): number => item.quantity)).toEqual([3]);
    } finally { localStorage.removeItem(key); }
  });

  it('retains a new guest basket while forwarding a completed receipt for transferred selections', (): void => {
    const key: string = 'natiart-cart:account:transferred-payment-tabs';
    localStorage.removeItem(key);
    try {
      service.addToCart(product({stockQuantity: 10}), 2).subscribe();
      service.rememberPurchase('transferred-cross-tab', 'cus_guest', [{cartItemId: service.getCartItemsSnapshot()[0].cartItemId, quantity: 2}]);
      const signedIn: CartService = new CartService(); signedIn.useAccount('transferred-payment-tabs');
      const newGuest: CartService = new CartService(); newGuest.addToCart(product({id: 'new-guest-piece'}), 1).subscribe();
      service.completeVerifiedPurchase('transferred-cross-tab');
      expect(new CartService().getCartItemsSnapshot().map((item: CartItem): string | undefined => item.product.id)).toEqual(['new-guest-piece']);
      signedIn.useAccount(null); signedIn.useAccount('transferred-payment-tabs');
      expect(signedIn.getCartItemsSnapshot().map((item: CartItem): string | undefined => item.product.id)).toEqual(['new-guest-piece']);
      signedIn.completeVerifiedPurchase('transferred-cross-tab');
      expect(signedIn.getCartItemsSnapshot()[0].quantity).toBe(1);
    } finally { localStorage.removeItem(key); }
  });

  it('records a checkout receipt without discarding another tab\'s newer selections', (): void => {
    service.addToCart(product(), 1).subscribe();
    const line: CartItem = service.getCartItemsSnapshot()[0];
    const otherTab: CartService = new CartService(); otherTab.addToCart(product({id: 'new-piece'}), 1).subscribe();
    service.rememberPurchase('recorded-cross-tab', 'cus_guest', [{cartItemId: line.cartItemId, quantity: 1}]);
    expect(new CartService().getCartItemsSnapshot().map((item: CartItem): string | undefined => item.product.id)).toEqual(['p1', 'new-piece']);
  });

  it('refuses stale cart settlement when the latest basket cannot be read and permits a safe retry', (): void => {
    service.addToCart(product(), 2).subscribe();
    service.rememberPurchase('read-blocked', 'cus_guest', [{cartItemId: service.getCartItemsSnapshot()[0].cartItemId, quantity: 2}]);
    const before: string = localStorage.getItem('natiart-cart')!;
    const read: jasmine.Spy = spyOn(localStorage, 'getItem').and.throwError('storage blocked');
    expect((): void => service.completeVerifiedPurchase('read-blocked')).toThrow();
    expect(service.getCartItemsSnapshot()[0].quantity).toBe(2);
    read.and.callThrough();
    expect(localStorage.getItem('natiart-cart')).toBe(before);
    service.completeVerifiedPurchase('read-blocked');
    expect(service.getCartItemsSnapshot()).toEqual([]);
  });

  it('keeps the live artwork file after a guest transfer and another mutation in the same account tab', (): void => {
    const key: string = 'natiart-cart:account:artwork-transfer';
    localStorage.removeItem(key);
    try {
      const artwork: File = new File(['test-artwork'], 'test.png', {type: 'image/png'});
      service.addToCart(product(), 1, false, artwork).subscribe();
      service.useAccount('artwork-transfer');
      service.addToCart(product({id: 'another-piece'}), 1).subscribe();
      expect(service.getCartItemsSnapshot()[0].image).toBe(artwork);
      expect(service.getCartItemsSnapshot()[0].requiresArtworkReselection).toBeFalsy();
    } finally { localStorage.removeItem(key); }
  });
  it('remaps colliding line IDs without settling another account selection', () => {
    localStorage.setItem('natiart-cart:account:collision', JSON.stringify({version: 1, items: [
      {cartItemId: 'same-id', product: product({stockQuantity: 10}), quantity: 2, goldBorder: false}], purchases: []}));
    localStorage.setItem('natiart-cart', JSON.stringify({version: 1, items: [
      {cartItemId: 'same-id', product: product({stockQuantity: 10}), quantity: 1, goldBorder: true}],
      purchases: [{orderId: 'guest-collision', customerId: 'cus_guest', lines: [{cartItemId: 'same-id', quantity: 1}], completed: false}]}));
    const restored: CartService = new CartService(); restored.useAccount('collision');
    restored.completeVerifiedPurchase('guest-collision');
    expect(restored.getCartItemsSnapshot().length).toBe(1); expect(restored.getCartItemsSnapshot()[0].goldBorder).toBeFalse();
    expect(restored.getCartItemsSnapshot()[0].quantity).toBe(2); localStorage.removeItem('natiart-cart:account:collision');
  });

});
