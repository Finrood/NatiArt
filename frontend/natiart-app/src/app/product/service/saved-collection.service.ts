import {DestroyRef, inject, Injectable, Signal, signal, WritableSignal} from '@angular/core';
import {DOCUMENT} from '@angular/common';
import {Product} from '../models/product.model';

export interface SavedPiece { id: string; label: string; }

const STORAGE_KEY: string = 'natiart-saved-collection-v1';
export const COLLECTION_LIMIT: number = 24;
const validId = (id: unknown): id is string => typeof id === 'string' && /^[a-zA-Z0-9_-]{1,200}$/.test(id);

/** A browser-owned shortlist. No account, price, artwork or purchase data is stored. */
@Injectable({providedIn: 'root'})
export class SavedCollectionService {
  private readonly _document: Document = inject(DOCUMENT);
  readonly $storageAvailable: WritableSignal<boolean> = signal<boolean>(true);
  private readonly $selection: WritableSignal<SavedPiece[]> = signal<SavedPiece[]>(this.restore());
  readonly $pieces: Signal<SavedPiece[]> = this.$selection.asReadonly();
  readonly $announcement: WritableSignal<string> = signal<string>('');
  private readonly $removed: WritableSignal<SavedPiece[]> = signal<SavedPiece[]>([]);
  readonly $undo: Signal<SavedPiece[]> = this.$removed.asReadonly();
  readonly limit: number = COLLECTION_LIMIT;

  constructor() {
    const window: Window | null = this._document.defaultView;
    const changed = (event: StorageEvent): void => {
      try {
        if ((event.key === STORAGE_KEY || event.key === null) && event.storageArea === window?.localStorage) {
          const pieces: SavedPiece[] = this.restore();
          if (this.$storageAvailable()) { this.$selection.set(pieces); this.$removed.set([]); }
        }
      } catch { this.$storageAvailable.set(false); }
    };
    window?.addEventListener('storage', changed);
    inject(DestroyRef).onDestroy((): void => window?.removeEventListener('storage', changed));
  }

  isSaved(id: string | undefined): boolean { return !!id && this.$pieces().some((piece: SavedPiece): boolean => piece.id === id); }

  toggle(product: Product): void {
    if (!validId(product.id)) return;
    this.sync();
    if (this.isSaved(product.id)) { this.remove(product.id); return; }
    this.saveMany([product]);
  }

  saveMany(products: Product[]): void {
    this.sync();
    const next: SavedPiece[] = [...this.$pieces()];
    let added: number = 0;
    for (const product of products) {
      if (!validId(product.id) || product.active === false || !product.label?.trim()
        || next.some((piece: SavedPiece): boolean => piece.id === product.id)) continue;
      if (next.length === this.limit) break;
      next.push({id: product.id, label: product.label.slice(0, 500)});
      added++;
    }
    this.$selection.set(next);
    this.$removed.set([]);
    this.$announcement.set(added === 1 ? $localize`:@@collectionAddedOne:Saved one piece to your collection.`
      : added ? $localize`:@@collectionAdded:Saved ${added}:COUNT: pieces to your collection.`
        : $localize`:@@collectionNothingAdded:No new pieces saved. Your collection holds up to 24 pieces.`);
    this.persist();
  }

  remove(id: string): void {
    this.sync();
    const piece: SavedPiece | undefined = this.$pieces().find((saved: SavedPiece): boolean => saved.id === id);
    if (!piece) return;
    this.$selection.update((pieces: SavedPiece[]): SavedPiece[] => pieces.filter((saved: SavedPiece): boolean => saved.id !== id));
    this.$removed.set([piece]);
    this.$announcement.set($localize`:@@collectionRemoved:Removed ${piece.label}:PIECE: from your collection.`);
    this.persist();
  }

  clear(): void {
    this.sync();
    this.$removed.set(this.$pieces());
    this.$selection.set([]);
    this.$announcement.set($localize`:@@collectionCleared:Your collection is cleared.`);
    this.persist();
  }

  undo(): void {
    this.sync();
    const combined: SavedPiece[] = [...this.$pieces()];
    for (const piece of this.$removed()) {
      if (!combined.some((saved: SavedPiece): boolean => saved.id === piece.id) && combined.length < this.limit) combined.push(piece);
    }
    this.$selection.set(combined);
    this.$removed.set([]);
    this.$announcement.set($localize`:@@collectionRestored:Your removed pieces are restored.`);
    this.persist();
  }

  private sync(): void {
    if (!this.$storageAvailable()) return;
    const pieces: SavedPiece[] = this.restore();
    if (this.$storageAvailable()) this.$selection.set(pieces);
  }

  private restore(): SavedPiece[] {
    try {
      const storage: Storage | undefined = this._document.defaultView?.localStorage;
      if (!storage) { this.$storageAvailable.set(false); return []; }
      const raw: string | null = storage.getItem(STORAGE_KEY);
      // Allow JSON escaping of all 24 bounded labels and identifiers.
      if (!raw || raw.length > 80000) return [];
      const parsed: unknown = JSON.parse(raw);
      if (!Array.isArray(parsed)) return [];
      const result: SavedPiece[] = [];
      for (const candidate of parsed as unknown[]) {
        if (!candidate || typeof candidate !== 'object') continue;
        const piece: Partial<SavedPiece> = candidate;
        if (validId(piece.id) && typeof piece.label === 'string' && piece.label.trim() && piece.label.length <= 500
          && !result.some((saved: SavedPiece): boolean => saved.id === piece.id)) result.push({id: piece.id, label: piece.label});
        if (result.length === COLLECTION_LIMIT) break;
      }
      return result;
    } catch { this.$storageAvailable.set(false); return []; }
  }

  private persist(): void {
    try {
      const storage: Storage | undefined = this._document.defaultView?.localStorage;
      if (!storage) throw new Error('Storage unavailable');
      if (this.$pieces().length) storage.setItem(STORAGE_KEY, JSON.stringify(this.$pieces()));
      else storage.removeItem(STORAGE_KEY);
      this.$storageAvailable.set(true);
    } catch {
      this.$storageAvailable.set(false);
      this.$announcement.set($localize`:@@collectionTemporary:Your collection works for this visit, but this browser could not save it. Copy its link to keep it.`);
    }
  }
}

/** Shared links accept a bounded, distinct list of public identifiers only. */
export function sharedCollectionIds(value: string | null): string[] | null {
  if (!value || value.length > 5000) return null;
  const ids: string[] = value.split(',');
  if (ids.length > COLLECTION_LIMIT || ids.some((id: string): boolean => !validId(id))) return null;
  return [...new Set(ids)];
}

export function collectionReturnContext(value: unknown): string | null {
  if (value === 'saved') return value;
  return typeof value === 'string' ? sharedCollectionIds(value)?.join(',') ?? null : null;
}
