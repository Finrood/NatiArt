import {inject, signal, WritableSignal, Component, ElementRef, OnDestroy, OnInit, Renderer2, ViewChild} from '@angular/core';
import { AsyncPipe, CurrencyPipe, NgStyle } from "@angular/common";
import {FormsModule} from "@angular/forms";
import {BehaviorSubject, Subscription, of} from "rxjs";
import {catchError, switchMap, tap} from "rxjs/operators";
import {Product} from "../../../models/product.model";
import {ActivatedRoute, ParamMap, RouterLink} from "@angular/router";
import {ProductService} from "../../../service/product.service";
import {Meta, Title} from '@angular/platform-browser';
import {TopMenuComponent} from "../top-menu/top-menu.component";
import {LeftMenuComponent} from "../left-menu/left-menu.component";
import {CartService} from "../../../service/cart.service";
import {PersonalizationOption} from "../../../models/support/personalization-option";
import {PersonalizationModalComponent} from "../personalization-modal/personalization-modal.component";
import {AddToCartButtonComponent} from "../add-to-cart-button/add-to-cart-button.component";
import {ButtonComponent} from "../../../../shared/components/button.component";
import {reportError} from '../../../../shared/service/error-reporting.service';

interface DetailImage { key: string; url: string | null; rawUrl: string | null; state: 'loading' | 'loaded' | 'error'; }
const EMPTY_IMAGE: string = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160"><rect width="160" height="160" fill="#eee"/><text x="80" y="80" text-anchor="middle" fill="#555">No image</text></svg>');

@Component({
  selector: 'app-product-detail',
  imports: [
    AsyncPipe,
    FormsModule,
    CurrencyPipe,
    TopMenuComponent,
    LeftMenuComponent,
    NgStyle,
    PersonalizationModalComponent,
    RouterLink,
    AddToCartButtonComponent,
    ButtonComponent
],
  templateUrl: './product-detail.component.html',
  styleUrls: ['./product-detail.component.css']
})
export class ProductDetailComponent implements OnInit, OnDestroy {
  imageLabel(index: number): string {
    return $localize`View product image ${index}:IMAGE_NUMBER:`;
  }

  product$ = new BehaviorSubject<Product | null>(null);
  quantity: number = 1;
  relatedProducts$ = new BehaviorSubject<Product[]>([]);
  selectedImageIndex: number = 0;
  readonly $productImages: WritableSignal<DetailImage[]> = signal([]);
  readonly $relatedImageUrls: WritableSignal<Record<string, string>> = signal({});
  readonly emptyImage: string = EMPTY_IMAGE;
  get imageUrls(): Record<number, string | undefined> {
    return Object.fromEntries(this.$productImages().map((entry: DetailImage, index: number): [number, string | undefined] => [index, entry.url ?? undefined]));
  }
  get relatedImageUrls(): Record<string, string> { return this.$relatedImageUrls(); }
  private readonly rawRelatedUrls: Map<string, string> = new Map();
  private imageSubscriptions: Subscription[] = [];
  readonly $zoomOrigin: WritableSignal<string> = signal('center center');
  isZoomed: boolean = false;
  zoomFactor: number = 5;
  lensSize: number = 100;
  @ViewChild('imageContainer') imageContainer!: ElementRef<HTMLDivElement>;
  @ViewChild('zoomLens') zoomLens!: ElementRef<HTMLDivElement>;
  @ViewChild('mainImage') mainImage!: ElementRef<HTMLImageElement>;
  private subscriptions: Subscription[] = [];
  private relatedSubscription: Subscription | null = null;
  private personalizationSubscription: Subscription | null = null;
  private imageRequestToken: number = 0;
  isLoading: boolean = true;
  loadError: string | null = null;

  showPersonalizationModal = false;
  selectedProductForModal: Product | null = null;
  triggerElementForModal: HTMLElement | null = null;

  private readonly _title: Title = inject(Title);
  private readonly _meta: Meta = inject(Meta);
  readonly categoryUnavailable: string = $localize`Category unavailable`;
  readonly packageUnavailable: string = $localize`Package unavailable`;

  private readonly _route: ActivatedRoute = inject(ActivatedRoute);
  private readonly _productService: ProductService = inject(ProductService);
  private readonly _renderer: Renderer2 = inject(Renderer2);
  private readonly _cartService: CartService = inject(CartService);


  get transformScale(): string {
    return `scale(${this.zoomFactor})`;
  }

  ngOnInit(): void {
    // Subscribe to param changes (not a one-shot snapshot): Angular reuses this
    // component when navigating between related products, and the inner
    // switchMap cancels any in-flight product fetch so stale responses cannot
    // overwrite the current view.
    const subscription = this._route.paramMap.pipe(
      tap((): void => {
        this.setStorefrontMetadata();
        this.isLoading = true;
        this.loadError = null;
        this.product$.next(null);
        this.quantity = 1;
        this.selectedImageIndex = 0;
        this.clearImages();
        this.closePersonalizationModal();
        this.relatedSubscription?.unsubscribe();
        this.relatedSubscription = null;
        this.relatedProducts$.next([]);
        this.isZoomed = false;
      }),
      switchMap((params: ParamMap) => {
        const productId: string | null = params.get('id');
        if (!productId) {
          this.loadError = $localize`Could not load this product. Please try again.`;
          this.isLoading = false;
          return of(null);
        }
        return this._productService.getProduct(productId).pipe(
          catchError((error: unknown) => {
            reportError('product-loading', error);
            this.product$.next(null);
            this.loadError = $localize`Could not load this product. Please try again.`;
            this.isLoading = false;
            return of(null);
          })
        );
      })
    ).subscribe({
      next: (product: Product | null): void => {
        if (!product) {
          return;
        }
        this.product$.next(product);
        this.setProductMetadata(product);
        this.updateProductImages(product);
        this.loadRelatedProducts(product.categoryId);
        this.isLoading = false;
      }
    });
    this.subscriptions.push(subscription);
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((subscription: Subscription): void => subscription.unsubscribe());
    this.clearImages();
    this.closePersonalizationModal();
    this.setStorefrontMetadata();
  }

  private setStorefrontMetadata(): void {
    this._title.setTitle($localize`NatiArt | Handmade Art`);
    this._meta.updateTag({
      name: 'description',
      content: $localize`Browse products from NatiArt.`
    });
    this._meta.updateTag({property: 'og:title', content: $localize`NatiArt | Handmade Art`});
    this._meta.updateTag({property: 'og:description', content: $localize`Browse products from NatiArt.`});
    this._meta.updateTag({property: 'og:type', content: 'website'});
  }

  private setProductMetadata(product: Product): void {
    const title = `${product.label} | NatiArt`;
    const description = (product.description || `${product.label} | NatiArt`)
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, 160);
    this._title.setTitle(title);
    this._meta.updateTag({name: 'description', content: description});
    this._meta.updateTag({property: 'og:title', content: title});
    this._meta.updateTag({property: 'og:description', content: description});
    this._meta.updateTag({property: 'og:type', content: 'product'});
  }

  private clearImages(): void {
    this.imageRequestToken++;
    this.imageSubscriptions.forEach((subscription: Subscription): void => subscription.unsubscribe());
    this.imageSubscriptions = [];
    this.$productImages().forEach((entry: DetailImage): void => { if (entry.rawUrl) URL.revokeObjectURL(entry.rawUrl); });
    this.rawRelatedUrls.forEach((url: string): void => URL.revokeObjectURL(url));
    this.rawRelatedUrls.clear();
    this.$productImages.set([]);
    this.$relatedImageUrls.set({});
  }

  selectImage(key: string | number): void {
    const index: number = typeof key === 'number' ? key : this.$productImages().findIndex((entry: DetailImage): boolean => entry.key === key);
    if (index < 0 || index >= this.$productImages().length) return;
    this.selectedImageIndex = index;
    this.isZoomed = false;
  }



  incrementQuantity(product: Product): void {
    if (this.quantity < product.stockQuantity) {
      this.quantity++;
    }
  }

  decrementQuantity(): void {
    if (this.quantity > 1) {
      this.quantity--;
    }
  }

  addToCart(product: Product, event?: MouseEvent): void {
    // Check if personalization is needed
    const needsPersonalization = product.availablePersonalizations?.some(
      p => p === PersonalizationOption.GOLDEN_BORDER || p === PersonalizationOption.CUSTOM_IMAGE
    );

    const triggerElement = event?.currentTarget as HTMLElement | undefined;

    if (needsPersonalization) {
      this.openPersonalizationModal(product, triggerElement);
    } else {
      this._cartService.addToCart(product, this.quantity);
      if (triggerElement) {
        this.triggerFlyAnimation(triggerElement);
      }
    }
  }

  toggleZoom(event: MouseEvent): void {
    if (!this.$productImages().length || this.$productImages()[this.selectedImageIndex]?.state !== 'loaded') return;
    this.isZoomed = !this.isZoomed;
    if (this.isZoomed) this.updateZoomPosition(event);
  }

  updateZoomPosition(event: MouseEvent): void {
    if (!this.isZoomed) return;

    if (!this.mainImage || !this.zoomLens || !this.imageContainer) return;
    const image = this.mainImage.nativeElement;
    const lens = this.zoomLens.nativeElement;
    const container = this.imageContainer.nativeElement;

    if (!container.offsetWidth || !container.offsetHeight || !image.isConnected || !lens.isConnected) return;
    const rect = container.getBoundingClientRect();
    let x = event.clientX - rect.left;
    let y = event.clientY - rect.top;

    x = Math.max(0, Math.min(x, container.offsetWidth));
    y = Math.max(0, Math.min(y, container.offsetHeight));

    const lensLeft = x - this.lensSize / 2;
    const lensTop = y - this.lensSize / 2;

    this._renderer.setStyle(lens, 'left', `${lensLeft}px`);
    this._renderer.setStyle(lens, 'top', `${lensTop}px`);

    const zoomX = (x / container.offsetWidth) * 100;
    const zoomY = (y / container.offsetHeight) * 100;

    this.$zoomOrigin.set(`${zoomX}% ${zoomY}%`);
  }


  openPersonalizationModal(product: Product, triggerElement?: HTMLElement): void {
    this.selectedProductForModal = product;
    this.triggerElementForModal = triggerElement || null;
    this.showPersonalizationModal = true;
  }

  closePersonalizationModal(): void {
    this.personalizationSubscription?.unsubscribe();
    this.personalizationSubscription = null;
    this.showPersonalizationModal = false;
    this.selectedProductForModal = null;
    this.triggerElementForModal = null;
  }

  onPersonalizationComplete(result: { goldBorder?: boolean, customImage?: File }): void {
    const selectedProduct: Product | null = this.selectedProductForModal;
    const triggerElement: HTMLElement | null = this.triggerElementForModal;
    if (!selectedProduct?.id) {
      this.closePersonalizationModal();
      return;
    }

    this.personalizationSubscription?.unsubscribe();
    this.personalizationSubscription = this._productService.getProduct(selectedProduct.id).subscribe({
      next: (currentProduct: Product): void => {
        const quantity: number = Math.min(this.quantity, currentProduct.stockQuantity);
        if (currentProduct.active === false || quantity <= 0) {
          this.closePersonalizationModal();
          return;
        }
        this._cartService.addToCart(currentProduct, quantity, result.goldBorder, result.customImage);
        if (triggerElement) {
          this.triggerFlyAnimation(triggerElement);
        }
        this.closePersonalizationModal();
      },
      error: (): void => this.closePersonalizationModal(),
    });
  }

  public triggerFlyAnimation(clickedElement: HTMLElement): void {
    const buttonElement = clickedElement.closest('button');
    if (!buttonElement) {
      reportError('product-loading');
      return;
    }

    const buttonRect = buttonElement.getBoundingClientRect();

    const cartContainer = document.querySelector('.cart-container');
    if (!cartContainer) {
      reportError('product-loading');
      return;
    }
    const cartRect = cartContainer.getBoundingClientRect();

    const flyEl = this._renderer.createElement('div');
    this._renderer.setStyle(flyEl, 'position', 'fixed');
    this._renderer.setStyle(flyEl, 'top', `${buttonRect.top + buttonRect.height / 2}px`); // Start from button center
    this._renderer.setStyle(flyEl, 'left', `${buttonRect.left + buttonRect.width / 2}px`); // Start from button center
    this._renderer.setStyle(flyEl, 'width', `15px`);
    this._renderer.setStyle(flyEl, 'height', `15px`);
    this._renderer.setStyle(flyEl, 'backgroundColor', 'var(--color-primary)'); // Use theme color
    this._renderer.setStyle(flyEl, 'borderRadius', '50%');
    this._renderer.setStyle(flyEl, 'opacity', '0.8');
    this._renderer.setStyle(flyEl, 'zIndex', '1000');
    this._renderer.setStyle(flyEl, 'transition', 'all 0.7s cubic-bezier(0.29, 0.56, 0.41, 1.31)'); // Ease-out-back like effect
    this._renderer.setStyle(flyEl, 'pointerEvents', 'none');

    this._renderer.appendChild(document.body, flyEl);

    flyEl.offsetWidth;

    const targetX = cartRect.left + cartRect.width / 2;
    const targetY = cartRect.top + cartRect.height / 2;

    this._renderer.setStyle(flyEl, 'top', `${targetY}px`);
    this._renderer.setStyle(flyEl, 'left', `${targetX}px`);
    this._renderer.setStyle(flyEl, 'transform', 'scale(0.1)'); // Shrink
    this._renderer.setStyle(flyEl, 'opacity', '0');

    setTimeout(() => {
      if (flyEl.parentNode === document.body) {
        this._renderer.removeChild(document.body, flyEl);
      }
    }, 700);
  }

  private updateProductImages(product: Product): void {
    const paths: string[] = Array.from(new Set(product.images ?? []));
    this.$productImages.set(paths.map((key: string): DetailImage => ({key, url: null, rawUrl: null, state: 'loading'})));
    paths.forEach((path: string): void => this.fetchImage(path));
    this.selectedImageIndex = 0;
  }

  private fetchImage(path: string): void {
    const token: number = this.imageRequestToken;
    const subscription: Subscription = this._productService.getImage(path).subscribe({
      next: (blob: Blob): void => {
        if (token !== this.imageRequestToken) return;
        const rawUrl: string = URL.createObjectURL(blob);
        this.$productImages.update((entries: DetailImage[]): DetailImage[] => entries.map((entry: DetailImage): DetailImage => entry.key === path ? {...entry, url: rawUrl, rawUrl, state: 'loaded'} : entry));
      },
      error: (): void => {
        if (token !== this.imageRequestToken) return;
        this.$productImages.update((entries: DetailImage[]): DetailImage[] => entries.map((entry: DetailImage): DetailImage => entry.key === path ? {...entry, url: EMPTY_IMAGE, state: 'error'} : entry));
      }
    });
    this.imageSubscriptions.push(subscription);
  }

  imageFailed(key: string): void {
    this.$productImages.update((entries: DetailImage[]): DetailImage[] => entries.map((entry: DetailImage): DetailImage => {
      if (entry.key !== key || entry.state === 'error') return entry;
      if (entry.rawUrl) URL.revokeObjectURL(entry.rawUrl);
      return {...entry, rawUrl: null, url: EMPTY_IMAGE, state: 'error'};
    }));
    this.isZoomed = false;
  }

  private loadRelatedProducts(categoryId: string | undefined): void {
    // Cancel any in-flight related-products fetch: without this, a slow first
    // response overwrites the related list of the product viewed second.
    if (this.relatedSubscription) {
      this.relatedSubscription.unsubscribe();
      this.relatedSubscription = null;
    }
    if (!categoryId) {
      this.relatedProducts$.next([]);
      return;
    }
    const subscription = this._productService.getProductsByCategory(categoryId).subscribe({
      next: (products: Product[]) => {
        const currentProductId = this.product$.value?.id;
        const related = products
          .filter(p => p.id !== currentProductId) // Exclude current product
          .slice(0, 4); // Limit to 4 related products
        this.relatedProducts$.next(related);
        // Load images for related products (simplified: assuming first image)
        related.forEach(p => {
          if (p.images && p.images.length > 0) {
            this.fetchRelatedProductImage(p.id!, p.images[0]); // Fetch only the first image for preview
          }
        });
      },
      error: (error) => reportError('related-products', error)
    });
    this.relatedSubscription = subscription;
    this.subscriptions.push(subscription);
  }


  private fetchRelatedProductImage(productId: string, imagePath: string): void {
    const token: number = this.imageRequestToken;
    const subscription: Subscription = this._productService.getImage(imagePath).subscribe({
      next: (blob: Blob): void => {
        if (token !== this.imageRequestToken) return;
        const rawUrl: string = URL.createObjectURL(blob);
        this.rawRelatedUrls.set(productId, rawUrl);
        this.$relatedImageUrls.update((urls: Record<string, string>): Record<string, string> => ({...urls, [productId]: rawUrl}));
      },
      error: (): void => {
        if (token !== this.imageRequestToken) return;
        this.$relatedImageUrls.update((urls: Record<string, string>): Record<string, string> => ({...urls, [productId]: EMPTY_IMAGE}));
      }
    });
    this.imageSubscriptions.push(subscription);
  }

  relatedImageFailed(id: string): void {
    if (this.$relatedImageUrls()[id] === EMPTY_IMAGE) return;
    const rawUrl: string | undefined = this.rawRelatedUrls.get(id);
    if (rawUrl) URL.revokeObjectURL(rawUrl);
    this.rawRelatedUrls.delete(id);
    this.$relatedImageUrls.update((urls: Record<string, string>): Record<string, string> => ({...urls, [id]: EMPTY_IMAGE}));
  }

  protected readonly PersonalizationOption = PersonalizationOption;
}
