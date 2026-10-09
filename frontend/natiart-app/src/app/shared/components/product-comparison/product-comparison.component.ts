import {afterNextRender, Component, DestroyRef, ElementRef, inject, Injector, OnDestroy, Signal, signal, viewChild, WritableSignal} from '@angular/core';
import {CurrencyPipe, DOCUMENT} from '@angular/common';
import {Event, NavigationEnd, NavigationSkipped, NavigationStart, Router, RouterLink} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Subscription} from 'rxjs';
import {AccessibleDialogComponent} from '../accessible-dialog.component';
import {ComparedPiece, ProductComparisonService} from '../../../product/service/product-comparison.service';
import {ProductService} from '../../../product/service/product.service';
import {Product} from '../../../product/models/product.model';
import {PersonalizationOption} from '../../../product/models/support/personalization-option';
import {EMPTY_PRODUCT_IMAGE, ImageCollection, ImageLoaderService, ImageResource} from '../../../product/service/image-loader.service';

type PieceState = 'loading' | 'loaded' | 'error';
interface PieceDetail { piece: ComparedPiece; state: PieceState; product: Product | null; }

@Component({selector: 'app-product-comparison', imports: [AccessibleDialogComponent, CurrencyPipe, RouterLink],
  templateUrl: './product-comparison.component.html', styleUrl: './product-comparison.component.css'})
export class ProductComparisonComponent implements OnDestroy {
  readonly comparison: ProductComparisonService = inject(ProductComparisonService);
  private readonly _products: ProductService = inject(ProductService);
  private readonly _images: ImageLoaderService = inject(ImageLoaderService);
  private readonly _router: Router = inject(Router);
  private readonly _destroyed: DestroyRef = inject(DestroyRef);
  private readonly _injector: Injector = inject(Injector);
  private readonly _document: Document = inject(DOCUMENT);
  private readonly $trayHeading: Signal<ElementRef<HTMLElement> | undefined> = viewChild<ElementRef<HTMLElement>>('trayHeading');
  private readonly requests: Map<string, Subscription> = new Map<string, Subscription>();
  private images: ImageCollection | null = null;
  readonly emptyImage: string = EMPTY_PRODUCT_IMAGE;
  readonly customImage: PersonalizationOption = PersonalizationOption.CUSTOM_IMAGE;
  readonly goldBorder: PersonalizationOption = PersonalizationOption.GOLDEN_BORDER;
  readonly $shopping: WritableSignal<boolean> = signal<boolean>(this.isShopping(this._router.url));
  readonly $open: WritableSignal<boolean> = signal<boolean>(false);
  readonly $details: WritableSignal<PieceDetail[]> = signal<PieceDetail[]>([]);

  constructor() {
    this._router.events.pipe(takeUntilDestroyed(this._destroyed)).subscribe((event: Event): void => {
      if (event instanceof NavigationStart || event instanceof NavigationSkipped) this.close();
      if (event instanceof NavigationEnd) this.$shopping.set(this.isShopping(event.urlAfterRedirects));
    });
  }

  open(): void {
    if (this.comparison.$pieces().length !== 2) return;
    this.close();
    this.images = this._images.create();
    this.$details.set(this.comparison.$pieces().map((piece: ComparedPiece): PieceDetail => ({piece, state: 'loading', product: null})));
    this.$open.set(true);
    for (const piece of this.comparison.$pieces()) this.load(piece.id);
  }

  load(id: string): void {
    this.requests.get(id)?.unsubscribe();
    this.update(id, 'loading', null);
    const request: Subscription = this._products.getProduct(id, true).subscribe({
      next: (product: Product): void => this.update(id, 'loaded', product),
      error: (): void => this.update(id, 'error', null)
    });
    this.requests.set(id, request);
  }

  retry(id: string): void {
    this.load(id);
    afterNextRender((): void => {
      if (this.$open() && this._document.activeElement === this._document.body) {
        this._document.getElementById('comparison-piece-' + id)?.focus({preventScroll: true});
      }
    }, {injector: this._injector});
  }

  private update(id: string, state: PieceState, product: Product | null): void {
    this.$details.update((details: PieceDetail[]): PieceDetail[] => details.map((detail: PieceDetail): PieceDetail =>
      detail.piece.id === id ? {...detail, state, product} : detail));
    this.images?.update(this.$details().map((detail: PieceDetail): ImageResource => ({key: detail.piece.id, source: detail.product?.images[0]})));
  }

  image(id: string): string { return this.images?.urls()[id] ?? this.emptyImage; }
  imageFailed(id: string): void { this.images?.failed(id); }
  removeLabel(label: string): string { return $localize`Remove ${label} from comparison`; }

  remove(id: string): void {
    this.close();
    this.comparison.remove(id);
    afterNextRender((): void => this.$trayHeading()?.nativeElement.focus({preventScroll: true}), {injector: this._injector});
  }

  clear(): void {
    this.close();
    this.comparison.clear();
    afterNextRender((): void => this._document.querySelector<HTMLElement>('[data-route-heading], main h1')?.focus({preventScroll: true}), {injector: this._injector});
  }

  close(): void {
    this.$open.set(false);
    for (const request of this.requests.values()) request.unsubscribe();
    this.requests.clear();
    this.images?.destroy();
    this.images = null;
    this.$details.set([]);
  }

  ngOnDestroy(): void { this.close(); }

  private isShopping(url: string): boolean { return /^\/(dashboard|products)([/?#]|$)/.test(url) || url.startsWith('/product/'); }
}
