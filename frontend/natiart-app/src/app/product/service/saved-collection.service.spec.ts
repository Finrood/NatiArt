import {TestBed} from '@angular/core/testing';
import {COLLECTION_LIMIT, SavedCollectionService, sharedCollectionIds} from './saved-collection.service';
import {Product} from '../models/product.model';

const key: string = 'natiart-saved-collection-v1';
const piece = (id: string): Product => ({id, label: 'Piece ' + id, categoryId: 'c', originalPrice: 100,
  markedPrice: 90, stockQuantity: 4, availablePersonalizations: [], images: [], tags: []});

describe('Browser-owned saved collection', (): void => {
  beforeEach((): void => { localStorage.removeItem(key); TestBed.configureTestingModule({}); });
  afterEach((): void => { localStorage.removeItem(key); });

  it('persists only public identity, toggles and restores after a new application instance', (): void => {
    const service: SavedCollectionService = TestBed.inject(SavedCollectionService);
    service.toggle({...piece('a'), description: 'Never store this', images: ['private-art']});
    expect(JSON.parse(localStorage.getItem(key)!)).toEqual([{id: 'a', label: 'Piece a'}]);
    TestBed.resetTestingModule();
    const restored: SavedCollectionService = TestBed.inject(SavedCollectionService);
    expect(restored.isSaved('a')).toBeTrue();
    restored.toggle(piece('a'));
    expect(localStorage.getItem(key)).toBeNull();
    expect(restored.$announcement()).toContain('Removed Piece a');
  });

  it('caps and deduplicates imported pieces, preserves sold-out choices and rejects inactive or invalid pieces', (): void => {
    const service: SavedCollectionService = TestBed.inject(SavedCollectionService);
    service.saveMany([piece('../private'), {...piece('hidden'), active: false}, {...piece('sold'), stockQuantity: 0},
      ...Array.from({length: 30}, (_: unknown, index: number): Product => piece('p' + index))]);
    expect(service.$pieces().length).toBe(COLLECTION_LIMIT);
    expect(service.isSaved('sold')).toBeTrue();
    expect(service.isSaved('hidden')).toBeFalse();
    service.saveMany([piece('sold')]);
    expect(service.$pieces().length).toBe(COLLECTION_LIMIT);
    service.toggle(piece('p0'));
    service.toggle(piece('extra'));
    expect(service.isSaved('extra')).toBeTrue();
    expect(service.$pieces().length).toBe(COLLECTION_LIMIT);
  });

  it('sanitizes malformed and oversized storage without retaining unexpected attributes', (): void => {
    localStorage.setItem(key, JSON.stringify([{id: '../bad', label: 'bad'}, {id: 'a', label: 'A', email: 'ignored'},
      {id: 'a', label: 'duplicate'}, {id: 'b', label: ' '.repeat(10)}, {id: 'c', label: 'x'.repeat(501)}]));
    expect(TestBed.inject(SavedCollectionService).$pieces()).toEqual([{id: 'a', label: 'A'}]);
    TestBed.resetTestingModule();
    localStorage.setItem(key, 'x'.repeat(80001));
    expect(TestBed.inject(SavedCollectionService).$pieces()).toEqual([]);
    TestBed.resetTestingModule();
    localStorage.setItem(key, '{broken');
    expect(TestBed.inject(SavedCollectionService).$pieces()).toEqual([]);
  });

  it('restores a full valid collection even when JSON escaping expands its labels', (): void => {
    const products: Product[] = Array.from({length: COLLECTION_LIMIT}, (_: unknown, index: number): Product =>
      ({...piece('p' + index), label: 'A' + '\u0001'.repeat(499)}));
    TestBed.inject(SavedCollectionService).saveMany(products);
    expect(localStorage.getItem(key)!.length).toBeGreaterThan(20000);
    TestBed.resetTestingModule();
    expect(TestBed.inject(SavedCollectionService).$pieces().map(saved => saved.id)).toEqual(products.map(product => product.id!));
  });

  it('keeps in-memory choices across storage failures and explains their temporary lifetime', (): void => {
    const service: SavedCollectionService = TestBed.inject(SavedCollectionService);
    service.toggle(piece('a'));
    spyOn(Storage.prototype, 'getItem').and.throwError('Blocked');
    spyOn(Storage.prototype, 'setItem').and.throwError('Full');
    service.toggle(piece('b'));
    service.toggle(piece('c'));
    expect(service.$pieces().map(saved => saved.id)).toEqual(['a', 'b', 'c']);
    expect(service.$storageAvailable()).toBeFalse();
    expect(service.$announcement()).toContain('Copy its link');
  });

  it('merges another tab before editing and synchronizes browser storage events', (): void => {
    const service: SavedCollectionService = TestBed.inject(SavedCollectionService);
    service.toggle(piece('a'));
    localStorage.setItem(key, JSON.stringify([{id: 'a', label: 'A'}, {id: 'b', label: 'B'}]));
    service.toggle(piece('c'));
    expect(service.$pieces().map(saved => saved.id)).toEqual(['a', 'b', 'c']);
    localStorage.removeItem(key);
    window.dispatchEvent(new StorageEvent('storage', {key, storageArea: localStorage}));
    expect(service.$pieces()).toEqual([]);
  });

  it('supports undo of removal and clearing without changing unrelated browser data', (): void => {
    localStorage.setItem('unrelated-collection-test', 'preserve');
    const service: SavedCollectionService = TestBed.inject(SavedCollectionService);
    service.saveMany([piece('a'), piece('b')]);
    service.remove('a');
    service.undo();
    expect(service.isSaved('a')).toBeTrue();
    service.clear();
    expect(service.$undo().length).toBe(2);
    expect(localStorage.getItem(key)).toBeNull();
    service.undo();
    expect(service.$pieces().length).toBe(2);
    expect(service.$undo()).toEqual([]);
    expect(localStorage.getItem('unrelated-collection-test')).toBe('preserve');
    localStorage.removeItem('unrelated-collection-test');
  });

  it('validates complete bounded shared links and never accepts paths or duplicate query payloads', (): void => {
    expect(sharedCollectionIds('a,b,a')).toEqual(['a', 'b']);
    for (const value of [null, '', 'a,../orders', 'a,', 'https://elsewhere', 'a?token=x', 'a,'.repeat(25)]) {
      expect(sharedCollectionIds(value)).toBeNull();
    }
    expect(sharedCollectionIds(Array.from({length: 24}, (_: unknown, index: number): string => 'a' + index).join(','))?.length).toBe(24);
  });
});
