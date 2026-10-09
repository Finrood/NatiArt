import {TestBed} from '@angular/core/testing';
import {ComparedPiece, ProductComparisonService} from './product-comparison.service';
import {Product} from '../models/product.model';

const piece = (id: string): Product => ({id, label: 'Piece ' + id, categoryId: 'c', originalPrice: 100,
  markedPrice: 90, stockQuantity: 4, availablePersonalizations: [], images: [], tags: []});

describe('Tab-local product comparison', (): void => {
  beforeEach((): void => { sessionStorage.removeItem('natiart-comparison-v1'); TestBed.configureTestingModule({}); });
  afterEach((): void => { sessionStorage.removeItem('natiart-comparison-v1'); });

  it('caps a pair, toggles a selected piece and persists only its identity and catalog context', (): void => {
    const service: ProductComparisonService = TestBed.inject(ProductComparisonService);
    service.toggle(piece('a'), {categoryId: 'c', query: 'porcelain', page: 2});
    service.toggle(piece('b'), {});
    service.toggle(piece('c'), {});
    expect(service.$pieces().map((selected: ComparedPiece): string => selected.id)).toEqual(['a', 'b']);
    expect(JSON.parse(sessionStorage.getItem('natiart-comparison-v1')!)[0]).toEqual({
      id: 'a', label: 'Piece a', context: {categoryId: 'c', query: 'porcelain', page: 2}});
    service.toggle(piece('a'), {});
    service.toggle(piece('c'), {});
    expect(service.$pieces().map((selected: ComparedPiece): string => selected.id)).toEqual(['b', 'c']);
    expect(service.$announcement()).toContain('2 of 2 pieces selected');
    service.clear();
    expect(sessionStorage.getItem('natiart-comparison-v1')).toBeNull();
  });

  it('restores a bounded distinct pair across reload while rejecting invalid IDs and sanitizing context', (): void => {
    sessionStorage.setItem('natiart-comparison-v1', JSON.stringify([
      {id: '../payments', label: 'Bad'}, {id: 'a', label: 'Piece a', context: {page: -1, query: false, unexpected: 'ignored'}},
      {id: 'a', label: 'Duplicate'}, {id: 'b', label: 'Piece b', context: {page: '2', categoryId: 'c'}},
      {id: 'c', label: 'Third'}]));
    const service: ProductComparisonService = TestBed.inject(ProductComparisonService);
    expect(service.$pieces()).toEqual([
      {id: 'a', label: 'Piece a', context: {page: 0, query: null, categoryId: null}},
      {id: 'b', label: 'Piece b', context: {page: 2, query: null, categoryId: 'c'}}]);
  });

  it('recovers from malformed or unavailable tab storage and keeps selection usable', (): void => {
    sessionStorage.setItem('natiart-comparison-v1', '{broken');
    const service: ProductComparisonService = TestBed.inject(ProductComparisonService);
    expect(service.$pieces()).toEqual([]);
    spyOn(Storage.prototype, 'setItem').and.throwError('Blocked');
    expect((): void => service.toggle(piece('a'), {})).not.toThrow();
    expect(service.isSelected('a')).toBeTrue();
  });
});
