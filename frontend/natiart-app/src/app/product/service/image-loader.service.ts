import {Injectable, inject, signal, Signal, WritableSignal} from '@angular/core';
import {Subscription} from 'rxjs';
import {ProductService} from './product.service';

export const EMPTY_PRODUCT_IMAGE: string = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160"><rect width="160" height="160" fill="#eee"/><text x="80" y="80" text-anchor="middle" fill="#555" font-size="14">No image</text></svg>');
export type ImageState = 'loading' | 'loaded' | 'error' | 'empty';
export interface ImageResource { key: string; source: string | Blob | undefined; }
interface ImageSlot { source: string | Blob | undefined; subscription: Subscription | null; rawUrl: string | null; }

/** Each view owns its URLs; ProductService only shares pending HTTP bytes. */
export class ImageCollection {
  private readonly slots: Map<string, ImageSlot> = new Map();
  private readonly $urls: WritableSignal<Record<string, string>> = signal<Record<string, string>>({});
  private readonly $states: WritableSignal<Record<string, ImageState>> = signal<Record<string, ImageState>>({});
  readonly urls: Signal<Record<string, string>> = this.$urls.asReadonly();
  readonly states: Signal<Record<string, ImageState>> = this.$states.asReadonly();
  private readonly invalidation: Subscription;
  private destroyed: boolean = false;

  constructor(private readonly products: ProductService) {
    this.invalidation = products.imageInvalidations.subscribe((): void => {
      const resources: ImageResource[] = Array.from(this.slots, ([key, slot]): ImageResource => ({key, source: slot.source}));
      this.clear();
      this.update(resources);
    });
  }

  update(resources: ImageResource[]): void {
    if (this.destroyed) return;
    const current: Set<string> = new Set(resources.map((resource: ImageResource): string => resource.key));
    for (const key of this.slots.keys()) if (!current.has(key)) this.remove(key);
    for (const resource of resources) {
      if (this.slots.has(resource.key) && this.slots.get(resource.key)?.source === resource.source) continue;
      this.remove(resource.key);
      const slot: ImageSlot = {source: resource.source, subscription: null, rawUrl: null};
      this.slots.set(resource.key, slot);
      this.publish(resource.key, EMPTY_PRODUCT_IMAGE, resource.source ? 'loading' : 'empty');
      if (resource.source instanceof Blob) this.loaded(resource.key, slot, resource.source);
      else if (resource.source) slot.subscription = this.products.getImage(resource.source).subscribe({
        next: (blob: Blob): void => this.loaded(resource.key, slot, blob),
        error: (): void => { if (this.slots.get(resource.key) === slot) this.publish(resource.key, EMPTY_PRODUCT_IMAGE, 'error'); }
      });
    }
  }

  failed(key: string): void {
    const slot: ImageSlot | undefined = this.slots.get(key);
    if (!slot || this.states()[key] === 'error' || this.states()[key] === 'empty') return;
    slot.subscription?.unsubscribe();
    if (slot.rawUrl) URL.revokeObjectURL(slot.rawUrl);
    slot.rawUrl = null;
    this.publish(key, EMPTY_PRODUCT_IMAGE, 'error');
  }

  destroy(): void { this.destroyed = true; this.invalidation.unsubscribe(); this.clear(); }

  private loaded(key: string, slot: ImageSlot, blob: Blob): void {
    if (this.destroyed || this.slots.get(key) !== slot) return;
    if (slot.rawUrl) URL.revokeObjectURL(slot.rawUrl);
    slot.rawUrl = URL.createObjectURL(blob);
    this.publish(key, slot.rawUrl, 'loaded');
  }
  private publish(key: string, url: string, state: ImageState): void {
    this.$urls.update((urls: Record<string, string>): Record<string, string> => ({...urls, [key]: url}));
    this.$states.update((states: Record<string, ImageState>): Record<string, ImageState> => ({...states, [key]: state}));
  }
  private remove(key: string): void {
    const slot: ImageSlot | undefined = this.slots.get(key);
    if (!slot) return;
    this.slots.delete(key);
    slot.subscription?.unsubscribe();
    if (slot.rawUrl) URL.revokeObjectURL(slot.rawUrl);
    this.$urls.update((urls: Record<string, string>): Record<string, string> => { const next: Record<string, string> = {...urls}; delete next[key]; return next; });
    this.$states.update((states: Record<string, ImageState>): Record<string, ImageState> => { const next: Record<string, ImageState> = {...states}; delete next[key]; return next; });
  }
  private clear(): void { for (const key of this.slots.keys()) this.remove(key); }
}

@Injectable({providedIn: 'root'})
export class ImageLoaderService {
  private readonly _products: ProductService = inject(ProductService);
  create(): ImageCollection { return new ImageCollection(this._products); }
}
