import {Component, ElementRef, OnDestroy, OnInit, Renderer2, ViewChild} from '@angular/core';
import { AsyncPipe, CurrencyPipe, KeyValuePipe, NgStyle } from "@angular/common";
import {FormsModule} from "@angular/forms";
import {BehaviorSubject, Subscription, of} from "rxjs";
import {catchError, switchMap, tap} from "rxjs/operators";
import {Product} from "../../../models/product.model";
import {ActivatedRoute, ParamMap, RouterLink} from "@angular/router";
import {ProductService} from "../../../service/product.service";
import {DomSanitizer, SafeUrl} from "@angular/platform-browser";
import {TopMenuComponent} from "../top-menu/top-menu.component";
import {LeftMenuComponent} from "../left-menu/left-menu.component";
import {CartService} from "../../../service/cart.service";
import {PersonalizationOption} from "../../../models/support/personalization-option";
import {PersonalizationModalComponent} from "../personalization-modal/personalization-modal.component";
import {AddToCartButtonComponent} from "../add-to-cart-button/add-to-cart-button.component";
import {ButtonComponent} from "../../../../shared/components/button.component";

@Component({
  selector: 'app-product-detail',
  standalone: true, // Add standalone: true if not already
  imports: [
    AsyncPipe,
    FormsModule,
    CurrencyPipe,
    TopMenuComponent,
    LeftMenuComponent,
    KeyValuePipe,
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
  product$ = new BehaviorSubject<Product | null>(null);
  quantity: number = 1;
  relatedProducts$ = new BehaviorSubject<Product[]>([]);
  selectedImageIndex: number = 0;
  imageUrls: { [index: number]: SafeUrl | null } = {};
  isZoomed: boolean = false;
  zoomFactor: number = 5;
  lensSize: number = 100;
  @ViewChild('imageContainer') imageContainer!: ElementRef<HTMLDivElement>;
  @ViewChild('zoomLens') zoomLens!: ElementRef<HTMLDivElement>;
  @ViewChild('mainImage') mainImage!: ElementRef<HTMLImageElement>;
  private subscriptions: Subscription[] = [];
  private relatedSubscription: Subscription | null = null;
  private imageRequestToken: number = 0;
  isLoading: boolean = true;
  loadError: string | null = null;

  showPersonalizationModal = false;
  selectedProductForModal: Product | null = null;
  triggerElementForModal: HTMLElement | null = null;

  constructor(
    private route: ActivatedRoute,
    private productService: ProductService,
    private sanitizer: DomSanitizer,
    private renderer: Renderer2,
    private cartService: CartService
  ) {}


  get transformScale(): string {
    return `scale(${this.zoomFactor})`;
  }

  ngOnInit(): void {
    // Subscribe to param changes (not a one-shot snapshot): Angular reuses this
    // component when navigating between related products, and the inner
    // switchMap cancels any in-flight product fetch so stale responses cannot
    // overwrite the current view.
    const subscription = this.route.paramMap.pipe(
      tap((): void => {
        this.isLoading = true;
        this.loadError = null;
        this.product$.next(null);
        this.quantity = 1;
        this.selectedImageIndex = 0;
        this.imageUrls = {};
        this.relatedProducts$.next([]);
        this.relatedImageUrls = {};
        // Invalidate in-flight main-image fetches for the previous product:
        // image slots are index-keyed, so a stale resolution must not write
        // into the newly reset map (see fetchImage).
        this.imageRequestToken++;
      }),
      switchMap((params: ParamMap) => {
        const productId: string | null = params.get('id');
        if (!productId) {
          throw new Error('Missing product id');
        }
        return this.productService.getProduct(productId);
      }),
      catchError((error: unknown) => {
        console.error('Error loading product:', error);
        this.product$.next(null);
        this.loadError = 'Could not load this product. Please try again.';
        this.isLoading = false;
        return of(null);
      })
    ).subscribe({
      next: (product: Product | null): void => {
        if (!product) {
          return;
        }
        this.product$.next(product);
        this.updateProductImages(product);
        this.loadRelatedProducts(product.categoryId);
        this.isLoading = false;
      }
    });
    this.subscriptions.push(subscription);
  }

  ngOnDestroy() {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
    Object.values(this.imageUrls).forEach(url => {
      if (url) {
        const urlString = this.sanitizer.sanitize(4 /* SecurityContext.RESOURCE_URL */, url);
        if (urlString) {
          URL.revokeObjectURL(urlString);
        }
      }
    });
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
      this.cartService.addToCart(product, this.quantity);
      if (triggerElement) {
        this.triggerFlyAnimation(triggerElement);
      }
    }
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

    this.renderer.setStyle(lens, 'left', `${lensLeft}px`);
    this.renderer.setStyle(lens, 'top', `${lensTop}px`);

    const zoomX = (x / container.offsetWidth) * 100;
    const zoomY = (y / container.offsetHeight) * 100;

    this.renderer.setStyle(image, 'transform-origin', `${zoomX}% ${zoomY}%`);
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
    if (this.selectedProductForModal) {
      this.cartService.addToCart(
        this.selectedProductForModal,
        this.quantity,
        result.goldBorder,
        result.customImage
      );
      if (this.triggerElementForModal) {
        this.triggerFlyAnimation(this.triggerElementForModal);
      }
    }
    this.closePersonalizationModal();
  }

  public triggerFlyAnimation(clickedElement: HTMLElement): void {
    const buttonElement = clickedElement.closest('button');
    if (!buttonElement) {
      console.error("Could not find button element for animation start.");
      return;
    }

    const buttonRect = buttonElement.getBoundingClientRect();

    const cartContainer = document.querySelector('.cart-container');
    if (!cartContainer) {
      console.error("Could not find cart container element (.cart-container) for animation target.");
      return;
    }
    const cartRect = cartContainer.getBoundingClientRect();

    const flyEl = this.renderer.createElement('div');
    this.renderer.setStyle(flyEl, 'position', 'fixed');
    this.renderer.setStyle(flyEl, 'top', `${buttonRect.top + buttonRect.height / 2}px`); // Start from button center
    this.renderer.setStyle(flyEl, 'left', `${buttonRect.left + buttonRect.width / 2}px`); // Start from button center
    this.renderer.setStyle(flyEl, 'width', `15px`);
    this.renderer.setStyle(flyEl, 'height', `15px`);
    this.renderer.setStyle(flyEl, 'backgroundColor', 'var(--color-primary)'); // Use theme color
    this.renderer.setStyle(flyEl, 'borderRadius', '50%');
    this.renderer.setStyle(flyEl, 'opacity', '0.8');
    this.renderer.setStyle(flyEl, 'zIndex', '1000');
    this.renderer.setStyle(flyEl, 'transition', 'all 0.7s cubic-bezier(0.29, 0.56, 0.41, 1.31)'); // Ease-out-back like effect
    this.renderer.setStyle(flyEl, 'pointerEvents', 'none');

    this.renderer.appendChild(document.body, flyEl);

    flyEl.offsetWidth;

    const targetX = cartRect.left + cartRect.width / 2;
    const targetY = cartRect.top + cartRect.height / 2;

    this.renderer.setStyle(flyEl, 'top', `${targetY}px`);
    this.renderer.setStyle(flyEl, 'left', `${targetX}px`);
    this.renderer.setStyle(flyEl, 'transform', 'scale(0.1)'); // Shrink
    this.renderer.setStyle(flyEl, 'opacity', '0');

    setTimeout(() => {
      if (flyEl.parentNode === document.body) {
        this.renderer.removeChild(document.body, flyEl);
      }
    }, 700);
  }

  private updateProductImages(product: Product): void {
    this.imageUrls = {};
    (product.images || []).forEach((imagePath, index) => {
      this.fetchImage(index, imagePath);
    });
    this.selectedImageIndex = 0;
  }


  private fetchImage(index: number, imagePath: string): void {
    // Prevent memory leaks by revoking old URLs if overwriting
    if (this.imageUrls[index]) {
      const oldUrl = this.sanitizer.sanitize(4, this.imageUrls[index]);
      if (oldUrl) URL.revokeObjectURL(oldUrl);
    }

    // Drop resolutions that arrive after a route-param reset: they belong to
    // the previously viewed product, and slots are index-keyed (not identity-keyed).
    const token: number = this.imageRequestToken;
    const subscription = this.productService.getImage(imagePath).subscribe({
      next: blob => {
        if (token !== this.imageRequestToken) {
          return;
        }
        const objectUrl = URL.createObjectURL(blob);
        this.imageUrls[index] = this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl);
        // Trigger change detection if necessary, though BehaviorSubject should handle it
        // this.product$.next(this.product$.value);
      },
      error: err => {
        if (token !== this.imageRequestToken) {
          return;
        }
        console.error(`Failed to load image at index ${index}:`, err);
        this.imageUrls[index] = 'assets/img/placeholder.png'; // Fallback image URL
      }
    });
    this.subscriptions.push(subscription);
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
    const subscription = this.productService.getProductsByCategory(categoryId).subscribe({
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
      error: (error) => console.error('Error loading related products:', error)
    });
    this.relatedSubscription = subscription;
    this.subscriptions.push(subscription);
  }

  // You might need a separate map for related product image URLs
  relatedImageUrls: { [productId: string]: SafeUrl | string } = {};

  private fetchRelatedProductImage(productId: string, imagePath: string): void {
    // Avoid re-fetching if URL already exists
    if (this.relatedImageUrls[productId]) return;

    const subscription = this.productService.getImage(imagePath).subscribe({
      next: blob => {
        const objectUrl = URL.createObjectURL(blob);
        this.relatedImageUrls[productId] = this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl);
        // Update the relatedProducts$ BehaviorSubject to trigger template update
        // This is a bit inefficient, ideally you'd update just the image URL part
        const currentRelated = this.relatedProducts$.value.map(p => {
          if (p.id === productId) {
            return { ...p, imageUrl: this.relatedImageUrls[productId] }; // Add a temporary imageUrl property perhaps
          }
          return p;
        });
        // If you modify the Product interface to include an optional 'displayImageUrl',
        // you could update that here for better binding in the template.
        // For now, just triggering an update might be enough if the template uses relatedImageUrls map.
        this.relatedProducts$.next([...this.relatedProducts$.value]); // Trigger update
      },
      error: err => {
        console.error(`Failed to load related image for product ${productId}:`, err);
        this.relatedImageUrls[productId] = 'assets/img/placeholder.png'; // Fallback
        this.relatedProducts$.next([...this.relatedProducts$.value]); // Trigger update even on error
      }
    });
    this.subscriptions.push(subscription);
  }

  protected readonly PersonalizationOption = PersonalizationOption;
}
