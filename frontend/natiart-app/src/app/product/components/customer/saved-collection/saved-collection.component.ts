import {afterNextRender, Component, computed, DestroyRef, effect, ElementRef, inject, Injector, OnDestroy, signal, Signal, untracked, viewChild, WritableSignal} from '@angular/core';
import {DOCUMENT, Location} from '@angular/common';
import {ActivatedRoute, ParamMap, RouterLink} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {HttpErrorResponse} from '@angular/common/http';
import {catchError, from, map, mergeMap, Observable, of, Subscription, timeout} from 'rxjs';
import {Product} from '../../../models/product.model';
import {ProductService} from '../../../service/product.service';
import {SavedCollectionService, SavedPiece, sharedCollectionIds} from '../../../service/saved-collection.service';
import {EMPTY_PRODUCT_IMAGE, ImageCollection, ImageLoaderService} from '../../../service/image-loader.service';
import {ProductCardComponent} from '../../../../shared/components/product-card/product-card.component';
import {CatalogContext} from '../../../../shared/models/catalog-context';

type PieceState = 'loading' | 'loaded' | 'unavailable' | 'error';
interface CollectionRow { id: string; label: string; state: PieceState; product: Product | null; }
interface SharedCollection { ids: string[]; invalid: boolean; }

@Component({selector: 'app-saved-collection', imports: [RouterLink, ProductCardComponent],
  templateUrl: './saved-collection.component.html', styleUrl: './saved-collection.component.css'})
export class SavedCollectionComponent implements OnDestroy {
  readonly collection: SavedCollectionService = inject(SavedCollectionService);
  private readonly _products: ProductService = inject(ProductService);
  private readonly _route: ActivatedRoute = inject(ActivatedRoute);
  private readonly _document: Document = inject(DOCUMENT);
  private readonly _location: Location = inject(Location);
  private readonly _injector: Injector = inject(Injector);
  private readonly _destroyRef: DestroyRef = inject(DestroyRef);
  readonly images: ImageCollection = inject(ImageLoaderService).create();
  readonly emptyImage: string = EMPTY_PRODUCT_IMAGE;
  readonly $rows: WritableSignal<CollectionRow[]> = signal<CollectionRow[]>([]);
  readonly $shared: WritableSignal<SharedCollection | null> = signal<SharedCollection | null>(null);
  readonly $busy: Signal<boolean> = computed<boolean>((): boolean => this.$rows().some((row: CollectionRow): boolean => row.state === 'loading'));
  readonly $shareOpen: WritableSignal<boolean> = signal<boolean>(false);
  readonly $catalogContext: Signal<CatalogContext> = computed<CatalogContext>((): CatalogContext =>
    ({collection: this.$shared() ? this.$rows().map((row: CollectionRow): string => row.id).join(',') : 'saved'}));
  readonly $copyMessage: WritableSignal<string> = signal<string>('');
  readonly $linkInput = viewChild<ElementRef<HTMLInputElement>>('shareLink');
  readonly $heading = viewChild<ElementRef<HTMLElement>>('heading');
  readonly $shareUrl: Signal<string> = computed<string>((): string => {
    const url: URL = new URL(this._location.prepareExternalUrl('/collection'), this._document.location.origin);
    url.searchParams.set('pieces', this.$rows().map((row: CollectionRow): string => row.id).join(','));
    return url.toString();
  });
  readonly $canKeep: Signal<boolean> = computed<boolean>((): boolean => this.$rows().some((row: CollectionRow): boolean =>
    row.state === 'loaded' && !this.collection.isSaved(row.id)) && this.collection.$pieces().length < this.collection.limit);
  private request: Subscription | null = null;
  private readonly retries: Map<string, Subscription> = new Map<string, Subscription>();
  private generation: number = 0;

  constructor() {
    this._route.queryParamMap.pipe(takeUntilDestroyed(this._destroyRef)).subscribe((params: ParamMap): void => {
      if (!params.has('pieces')) { this.$shared.set(null); return; }
      const ids: string[] | null = params.getAll('pieces').length === 1 ? sharedCollectionIds(params.get('pieces')) : null;
      this.$shared.set({ids: ids ?? [], invalid: ids === null});
    });
    effect((): void => {
      const shared: SharedCollection | null = this.$shared();
      const pieces: SavedPiece[] = shared ? shared.ids.map((id: string, index: number): SavedPiece =>
        ({id, label: $localize`:@@collectionPiece:Piece ${index + 1}:NUMBER:`})) : this.collection.$pieces();
      untracked((): void => this.synchronize(pieces));
    });
  }

  private synchronize(pieces: SavedPiece[], refresh: boolean = false): void {
    this.cancel();
    this.$copyMessage.set('');
    if (!pieces.length) this.$shareOpen.set(false);
    const previous: CollectionRow[] = refresh ? [] : this.$rows();
    this.$rows.set(pieces.map((piece: SavedPiece): CollectionRow => {
      const old: CollectionRow | undefined = previous.find((row: CollectionRow): boolean => row.id === piece.id);
      return old ?? {...piece, state: 'loading', product: null};
    }));
    this.updateImages();
    const generation: number = this.generation;
    this.request = from(this.$rows().filter((row: CollectionRow): boolean => row.state === 'loading')).pipe(
      mergeMap((row: CollectionRow): Observable<CollectionRow> => this.fetch(row), 4)
    ).subscribe((row: CollectionRow): void => { if (generation === this.generation) this.update(row); });
  }

  private fetch(row: CollectionRow): Observable<CollectionRow> {
    return this._products.getProduct(row.id, true).pipe(
      timeout(10000),
      map((product: Product): CollectionRow => product.id === row.id && product.active !== false
        ? {...row, label: product.label, state: 'loaded', product} : {...row, state: 'unavailable', product: null}),
      catchError((error: unknown): Observable<CollectionRow> => of({...row, product: null,
        state: error instanceof HttpErrorResponse && [403, 404, 410].includes(error.status) ? 'unavailable' : 'error'}))
    );
  }

  retry(id: string): void {
    const row: CollectionRow | undefined = this.$rows().find((piece: CollectionRow): boolean => piece.id === id);
    if (!row) return;
    this.retries.get(id)?.unsubscribe();
    this.update({...row, state: 'loading', product: null});
    const generation: number = this.generation;
    this.retries.set(id, this.fetch(row).subscribe((updated: CollectionRow): void => {
      if (generation === this.generation) this.update(updated);
    }));
    this.focusHeading();
  }

  refresh(): void { this.synchronize(this.$rows().map((row: CollectionRow): SavedPiece => ({id: row.id, label: row.label})), true); }

  keep(): void {
    this.collection.saveMany(this.$rows().flatMap((row: CollectionRow): Product[] => row.product ? [row.product] : []));
  }

  remove(id: string): void { this.collection.remove(id); this.focusHeading(); }
  clear(): void { this.collection.clear(); this.focusHeading(); }
  undo(): void { this.collection.undo(); this.focusHeading(); }
  removeLabel(label: string): string { return $localize`:@@collectionRemoveName:Remove ${label}:PIECE: from your collection`; }

  share(): void {
    this.$shareOpen.set(true);
    afterNextRender((): void => { this.$linkInput()?.nativeElement.focus(); this.$linkInput()?.nativeElement.select(); }, {injector: this._injector});
  }

  async copy(): Promise<void> {
    const url: string = this.$shareUrl();
    try {
      const clipboard: Clipboard | undefined = this._document.defaultView?.navigator.clipboard;
      if (!clipboard) throw new Error('Clipboard unavailable');
      await clipboard.writeText(url);
      if (!this._destroyRef.destroyed && url === this.$shareUrl()) this.$copyMessage.set($localize`:@@collectionCopied:Collection link copied.`);
    } catch {
      if (!this._destroyRef.destroyed) this.$copyMessage.set($localize`:@@collectionCopyFallback:Copy the link from the field above. Automatic copying is unavailable in this browser.`);
    }
  }

  private focusHeading(): void {
    afterNextRender((): void => this.$heading()?.nativeElement.focus({preventScroll: true}), {injector: this._injector});
  }

  private update(row: CollectionRow): void {
    this.$rows.update((rows: CollectionRow[]): CollectionRow[] => rows.map((old: CollectionRow): CollectionRow => old.id === row.id ? row : old));
    this.updateImages();
  }

  private updateImages(): void {
    this.images.update(this.$rows().map((row: CollectionRow) => ({key: row.id, source: row.product?.images?.[0]})));
  }

  private cancel(): void {
    this.generation++;
    this.request?.unsubscribe();
    for (const request of this.retries.values()) request.unsubscribe();
    this.retries.clear();
  }

  ngOnDestroy(): void { this.cancel(); this.images.destroy(); }
}
