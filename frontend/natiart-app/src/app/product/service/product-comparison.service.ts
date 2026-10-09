import {inject, Injectable, Signal, signal, WritableSignal} from '@angular/core';
import {DOCUMENT} from '@angular/common';
import {Product} from '../models/product.model';
import {CatalogContext} from '../../shared/models/catalog-context';

export interface ComparedPiece {
  id: string;
  label: string;
  context: CatalogContext;
}

const STORAGE_KEY: string = 'natiart-comparison-v1';

@Injectable({providedIn: 'root'})
export class ProductComparisonService {
  private readonly _document: Document = inject(DOCUMENT);
  private readonly $selection: WritableSignal<ComparedPiece[]> = signal<ComparedPiece[]>(this.restore());
  readonly $pieces: Signal<ComparedPiece[]> = this.$selection.asReadonly();
  readonly $announcement: WritableSignal<string> = signal<string>('');

  isSelected(id: string | undefined): boolean {
    return !!id && this.$pieces().some((piece: ComparedPiece): boolean => piece.id === id);
  }

  toggle(product: Product, context: CatalogContext): void {
    if (!product.id || !/^[a-zA-Z0-9_-]{1,200}$/.test(product.id)) return;
    if (this.isSelected(product.id)) { this.remove(product.id); return; }
    if (this.$pieces().length >= 2) return;
    const piece: ComparedPiece = {id: product.id, label: product.label, context: this.cleanContext(context)};
    this.$selection.update((pieces: ComparedPiece[]): ComparedPiece[] => [...pieces, piece]);
    this.$announcement.set($localize`Selected for comparison: ${product.label}. ${this.$pieces().length} of 2 pieces selected.`);
    this.persist();
  }

  remove(id: string): void {
    const piece: ComparedPiece | undefined = this.$pieces().find((selected: ComparedPiece): boolean => selected.id === id);
    if (!piece) return;
    this.$selection.update((pieces: ComparedPiece[]): ComparedPiece[] => pieces.filter((selected: ComparedPiece): boolean => selected.id !== id));
    this.$announcement.set($localize`Removed from comparison: ${piece.label}. ${this.$pieces().length} of 2 pieces selected.`);
    this.persist();
  }

  clear(): void {
    this.$selection.set([]);
    this.$announcement.set($localize`Comparison cleared.`);
    this.persist();
  }

  private cleanContext(context: CatalogContext): CatalogContext {
    const page: number = Number(context.page ?? 0);
    return {
      categoryId: typeof context.categoryId === 'string' ? context.categoryId.slice(0, 200) : null,
      query: typeof context.query === 'string' ? context.query.slice(0, 200) : null,
      page: Number.isSafeInteger(page) && page >= 0 ? page : 0
    };
  }

  private restore(): ComparedPiece[] {
    try {
      const raw: string | null | undefined = this._document.defaultView?.sessionStorage.getItem(STORAGE_KEY);
      if (!raw || raw.length > 5000) return [];
      const parsed: unknown = JSON.parse(raw);
      if (!Array.isArray(parsed)) return [];
      const restored: ComparedPiece[] = [];
      for (const candidate of parsed as unknown[]) {
        if (!candidate || typeof candidate !== 'object') continue;
        const piece: Partial<ComparedPiece> = candidate;
        if (typeof piece.id !== 'string' || !/^[a-zA-Z0-9_-]{1,200}$/.test(piece.id)
          || typeof piece.label !== 'string' || !piece.label.trim() || piece.label.length > 500
          || restored.some((selected: ComparedPiece): boolean => selected.id === piece.id)) continue;
        restored.push({id: piece.id, label: piece.label,
          context: this.cleanContext(piece.context && typeof piece.context === 'object' ? piece.context : {})});
        if (restored.length === 2) break;
      }
      return restored;
    } catch { return []; }
  }

  private persist(): void {
    try {
      const storage: Storage | undefined = this._document.defaultView?.sessionStorage;
      if (this.$pieces().length) storage?.setItem(STORAGE_KEY, JSON.stringify(this.$pieces()));
      else storage?.removeItem(STORAGE_KEY);
    } catch { /* Browsing still works when tab storage is unavailable. */ }
  }
}
