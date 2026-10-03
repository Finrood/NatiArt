import {ImageCollection, ImageLoaderService, EMPTY_PRODUCT_IMAGE} from '../../../service/image-loader.service';
import {Component, inject, ElementRef, OnDestroy, OnInit, Renderer2, ViewChild} from '@angular/core';
import { AsyncPipe, CurrencyPipe, NgStyle } from "@angular/common";
import {FormsModule} from "@angular/forms";
import {BehaviorSubject, Subscription, of} from "rxjs";
import {catchError, switchMap, tap} from "rxjs/operators";
import {Product} from "../../../models/product.model";
import {ActivatedRoute, ParamMap, RouterLink} from "@angular/router";
import {ProductService} from "../../../service/product.service";
import {Meta, Title} from "@angular/platform-browser";
import {TopMenuComponent} from "../top-menu/top-menu.component";
import {LeftMenuComponent} from "../left-menu/left-menu.component";
import {CartService} from "../../../service/cart.service";
import {PersonalizationOption} from "../../../models/support/personalization-option";
import {PersonalizationModalComponent} from "../personalization-modal/personalization-modal.component";
import {AddToCartButtonComponent} from "../add-to-cart-button/add-to-cart-button.component";
import {ButtonComponent} from "../../../../shared/components/button.component";
import {reportError} from '../../../../shared/service/error-reporting.service';

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
  readonly images: ImageCollection = inject(ImageLoaderService).create();
  readonly emptyImage: string = EMPTY_PRODUCT_IMAGE;
  get imageUrls(): Record<string, string> { return this.images.urls(); }
  imageLabel(index: number): string {
    return $localize`View product image ${index}:IMAGE_NUMBER:`;
  }

  product$ = new BehaviorSubject<Product | null>(null);
  quantity: number = 1;
  relatedProducts$ = new BehaviorSubject<Product[]>([]);
  selectedImageIndex: number = 0;
  isZoomed: boolean = false;
  zoomFactor: number = 5;
  lensSize: number = 100;
  @ViewChild('imageContainer') imageContainer!: ElementRef<HTMLDivElement>;
  @ViewChild('zoomLens') zoomLens!: ElementRef<HTMLDivElement>;
  @ViewChild('mainImage') mainImage!: ElementRef<HTMLImageElement>;
  private subscriptions: Subscription[] = [];
  private relatedSubscription: Subscription | null = null;
  isLoading: boolean = true;
  loadError: string | null = null;

  showPersonalizationModal = false;
  selectedProductForModal: Product | null = null;
  triggerElementForModal: HTMLElement | null = null;

  private readonly _route: ActivatedRoute = inject(ActivatedRoute);
  private readonly _productService: ProductService = inject(ProductService);
  private readonly _renderer: Renderer2 = inject(Renderer2);
  private readonly _cartService: CartService = inject(CartService);

  private readonly _title = inject(Title);
  private readonly _meta = inject(Meta);


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
        this.images.update([]);
        this.relatedProducts$.next([]);
      }),
      switchMap((params: ParamMap) => {
        const productId: string | null = params.get('id');
        if (!productId) {
          throw new Error($localize`Missing product id`);
        }
        return this._productService.getProduct(productId);
      }),
      catchError((error: unknown) => {
        reportError('product-loading', error);
        this.product$.next(null);
        this.loadError = $localize`Could not load this product. Please try again.`;
        this.isLoading = false;
        return of(null);
      })
    ).subscribe({
      next: (product: Product | null): void => {
        if (!product) {
          return;
        }
        this.product$.next(product);
        this.setProductMetadata(product);
        this.updateImages();
        this.loadRelatedProducts(product.categoryId);
        this.isLoading = false;
      }
    });
    this.subscriptions.push(subscription);
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
    this.images.destroy();
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


  selectImage(index: number) {
    this.selectedImageIndex = index;
  }

  incrementQuantity(product: Product) {
    if (this.quantity < product.stockQuantity) {
      this.quantity++;
    }
  }

  decrementQuantity() {
    if (this.quantity > 1) {
      this.quantity--;
    }
  }

  addToCart(product: Product, event?: MouseEvent) { // Added optional event parameter
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

  zoomKey(event: Event): void {
    event.preventDefault();
    const element = event.currentTarget as HTMLElement;
    const rect = element.getBoundingClientRect();
    element.dispatchEvent(new MouseEvent('click', {bubbles: true, clientX: rect.left + rect.width / 2, clientY: rect.top + rect.height / 2}));
  }

  toggleZoom(event: MouseEvent) {
    this.isZoomed = !this.isZoomed;
    if (this.isZoomed) {
      this.updateZoomPosition(event);
    }
  }

  updateZoomPosition(event: MouseEvent) {
    if (!this.isZoomed) return;

    const image = this.mainImage.nativeElement;
    const lens = this.zoomLens.nativeElement;
    const container = this.imageContainer.nativeElement;

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

    this._renderer.setStyle(image, 'transform-origin', `${zoomX}% ${zoomY}%`);
  }


  openPersonalizationModal(product: Product, triggerElement?: HTMLElement) {
    this.selectedProductForModal = product;
    this.triggerElementForModal = triggerElement || null;
    this.showPersonalizationModal = true;
  }

  closePersonalizationModal() {
    this.showPersonalizationModal = false;
    this.selectedProductForModal = null;
    this.triggerElementForModal = null;
  }

  onPersonalizationComplete(result: { goldBorder?: boolean, customImage?: File }) {
    const selectedProduct: Product | null = this.selectedProductForModal;
    const triggerElement: HTMLElement | null = this.triggerElementForModal;
    if (!selectedProduct?.id) {
      this.closePersonalizationModal();
      return;
    }

    this._productService.getProduct(selectedProduct.id).subscribe({
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
    };
    const subscription = this._productService.getProductsByCategory(categoryId).subscribe({
      next: (products: Product[]) => {
        const currentProductId = this.product$.value?.id;
        const related = products
          .filter(p => p.id !== currentProductId) // Exclude current product
          .slice(0, 4); // Limit to 4 related products
        this.relatedProducts$.next(related);
        this.updateImages();
      },
      error: (error) => reportError('related-products', error)
    });
    this.relatedSubscription = subscription;
    this.subscriptions.push(subscription);
  }

  get relatedImageUrls(): Record<string, string> { return this.images.urls(); }

  private updateImages(): void {
    this.images.update([
      ...(this.product$.value?.images ?? []).map((source: string, index: number) => ({key: String(index), source})),
      ...this.relatedProducts$.value.map((product: Product) => ({key: product.id!, source: product.images?.[0]}))
    ]);
  }

  protected readonly PersonalizationOption = PersonalizationOption;
}
