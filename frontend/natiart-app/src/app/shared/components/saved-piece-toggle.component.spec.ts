import {ComponentFixture, TestBed} from '@angular/core/testing';
import {SavedPieceToggleComponent} from './saved-piece-toggle.component';
import {SavedCollectionService} from '../../product/service/saved-collection.service';
import {Product} from '../../product/models/product.model';

describe('Save piece toggle', (): void => {
  let fixture: ComponentFixture<SavedPieceToggleComponent>;
  const product: Product = {id: 'a', label: 'Test plate', categoryId: 'c', originalPrice: 100, markedPrice: 90,
    stockQuantity: 0, availablePersonalizations: [], images: [], tags: []};
  beforeEach((): void => {
    localStorage.removeItem('natiart-saved-collection-v1');
    TestBed.configureTestingModule({imports: [SavedPieceToggleComponent]});
    fixture = TestBed.createComponent(SavedPieceToggleComponent);
    fixture.componentRef.setInput('product', product);
    fixture.detectChanges();
  });
  afterEach((): void => { fixture.destroy(); localStorage.removeItem('natiart-saved-collection-v1'); });

  it('uses a stable named pressed-state toggle and allows saving an out-of-stock piece', (): void => {
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    const name: string | null = button.getAttribute('aria-label');
    button.click(); fixture.detectChanges();
    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(button.getAttribute('aria-label')).toBe(name);
    expect(button.textContent).toContain('Saved');
    button.click(); fixture.detectChanges();
    expect(button.getAttribute('aria-pressed')).toBe('false');
  });

  it('keeps saved pieces removable at the limit and prevents an extra selection', (): void => {
    const service: SavedCollectionService = TestBed.inject(SavedCollectionService);
    service.saveMany(Array.from({length: 24}, (_: unknown, index: number): Product => ({...product, id: 'p' + index})));
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(button.disabled).toBeTrue();
    fixture.componentRef.setInput('product', {...product, id: 'p0'}); fixture.detectChanges();
    expect(button.disabled).toBeFalse();
    button.click(); fixture.detectChanges();
    expect(service.$pieces().length).toBe(23);
  });
});
