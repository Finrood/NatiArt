import {Component, computed, inject, input, InputSignal, Signal} from '@angular/core';
import {Product} from '../../product/models/product.model';
import {SavedCollectionService} from '../../product/service/saved-collection.service';

@Component({selector: 'app-saved-piece-toggle', template: `
  <button type="button" [attr.aria-label]="$label()" [attr.aria-pressed]="collection.isSaved($product().id)"
    [disabled]="!collection.isSaved($product().id) && collection.$pieces().length >= collection.limit"
    (click)="collection.toggle($product())">
    <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"
      [class.saved]="collection.isSaved($product().id)"><path d="M6 3h12v18l-6-4-6 4V3Z"/></svg>
    @if (collection.isSaved($product().id)) { <span i18n="@@collectionSaved">Saved</span> }
    @else if ($compact()) { <span i18n="@@collectionSaveShort">Save</span> }
    @else { <span i18n="@@collectionSave">Save piece</span> }
  </button>`, styles: `
    :host { display:block; position:relative; z-index:1; }
    button { display:flex; align-items:center; gap:.5rem; min-height:2.75rem; color:rgb(var(--primary)); font-size:.75rem; text-align:left; }
    svg { width:1rem; height:1rem; flex-shrink:0; } svg.saved { fill:rgb(var(--primary-light) / .4); }
    button:hover:enabled span { text-decoration:underline; text-underline-offset:.25em; }
    button:disabled { opacity:.6; cursor:default; }
  `})
export class SavedPieceToggleComponent {
  readonly collection: SavedCollectionService = inject(SavedCollectionService);
  readonly $product: InputSignal<Product> = input.required<Product>({alias: 'product'});
  readonly $compact: InputSignal<boolean> = input<boolean>(false, {alias: 'compact'});
  readonly $label: Signal<string> = computed<string>((): string => $localize`:@@collectionSaveName:Save ${this.$product().label}:PIECE: to your collection`);
}
