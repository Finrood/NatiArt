import {Component, ElementRef, Input, OnDestroy, OnInit, Renderer2} from '@angular/core';
import { AsyncPipe, CurrencyPipe } from "@angular/common";
import {BehaviorSubject, Subscription} from "rxjs";
import {Product} from "../../../../models/product.model";
import {ProductService} from "../../../../service/product.service";
import {RouterLink} from "@angular/router";
import {DomSanitizer, SafeUrl} from "@angular/platform-browser";
import {CartService} from "../../../../service/cart.service";
import {PersonalizationModalComponent} from "../../personalization-modal/personalization-modal.component";
import {PersonalizationOption} from "../../../../models/support/personalization-option";
import {AddToCartButtonComponent} from "../../add-to-cart-button/add-to-cart-button.component";
import {reportError} from '../../../../../shared/service/error-reporting.service';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [AsyncPipe, CurrencyPipe, RouterLink, PersonalizationModalComponent, AddToCartButtonComponent],
  templateUrl: './product-list.component.html',
  styleUrls: ['./product-list.component.css']
})
export class ProductListComponent implements OnInit, OnDestroy {
  @Input() type: 'featured' | 'new' = 'featured';
  @Input() title: string = '';

  products = new BehaviorSubject<Product[]>([]);
  imageUrls: { [productId: string]: SafeUrl | null } = {};
  private subscriptions: Subscription[] = [];
  private objectUrls: string[] = [];
  private rawObjectUrlById: Map<string, string> = new Map<string, string>();

  // Personalization modal
  showPersonalizationModal = false;
  selectedProduct: Product | null = null;

  constructor(
    private productService: ProductService,
    private cartService: CartService,
    private sanitizer: DomSanitizer,
    private renderer: Renderer2, // Inject Renderer2
    private elRef: ElementRef // Inject ElementRef
  ) {}

  ngOnInit() {
    this.getProducts();
  }

  ngOnDestroy() {
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.rawObjectUrlById.forEach((rawUrl: string): void => URL.revokeObjectURL(rawUrl));
    this.rawObjectUrlById.clear();
    this.objectUrls = [];
  }

  private getProducts(): void {
    const productObservable = this.type === 'featured'
      ? this.productService.getFeaturedProducts()
      : this.productService.getNewProducts();
    const sub = productObservable.subscribe({
      next: (response) => {
        this.products.next(response);
        this.updateProductImages(response);
      },
      error: (error) => reportError('product-loading', error)
    });
    this.subscriptions.push(sub);
  }

  private updateProductImages(products: Product[]): void {
    // AS2: prune ids that left the listing — without a removal pass a
    // refreshed listing that drops a product keeps its blob URL until teardown.
    const liveIds: Set<string> = new Set<string>();
    products.forEach((product: Product): void => {
      if (product.id) {
        liveIds.add(product.id);
      }
    });
    Object.keys(this.imageUrls).forEach((productId: string): void => {
      if (!liveIds.has(productId)) {
        this.revokeObjectUrl(productId);
        delete this.imageUrls[productId];
      }
    });
    products.forEach(product => {
      if (!product.id) {
        return;
      }
      // Skip lines already loading/loaded: without this guard every emission
      // re-issues one image GET per card and leaks one blob URL per card.
      if (this.imageUrls[product.id] !== undefined) {
        return;
      }
      // Mark the slot before the async fetch so concurrent emissions share it.
      this.imageUrls[product.id] = null;
      if (product.images && product.images.length > 0) {
        this.fetchImage(product.id, product.images[0]);
      }
    });
  }

  private revokeObjectUrl(productId: string): void {
    const rawUrl: string | undefined = this.rawObjectUrlById.get(productId);
    if (rawUrl) {
      URL.revokeObjectURL(rawUrl);
      this.rawObjectUrlById.delete(productId);
      const index: number = this.objectUrls.indexOf(rawUrl);
      if (index > -1) {
        this.objectUrls.splice(index, 1);
      }
    }
  }

  private fetchImage(productId: string, imagePath: string): void {
    const sub = this.productService.getImage(imagePath).subscribe({
      next: (blob: Blob): void => {
        // AS2 twin: revoke-before-overwrite so a re-fetch for the same id
        // frees the previous blob instead of orphaning it.
        this.revokeObjectUrl(productId);
        const objectUrl: string = URL.createObjectURL(blob);
        this.objectUrls.push(objectUrl);
        this.rawObjectUrlById.set(productId, objectUrl);
        this.imageUrls[productId] = this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl);
        this.products.next([...this.products.value]);
      },
      error: (): void => {
        this.revokeObjectUrl(productId);
        this.imageUrls[productId] = 'assets/img/placeholder.png';
        this.products.next([...this.products.value]);
      }
    });
    this.subscriptions.push(sub);
  }

  addToCart(product: Product, event: MouseEvent) {
    const personalizations: PersonalizationOption[] = product.availablePersonalizations ?? [];
    if (personalizations.includes(PersonalizationOption.GOLDEN_BORDER) || personalizations.includes(PersonalizationOption.CUSTOM_IMAGE)) {
      // If personalization is needed, store the event target for later animation
      this.openPersonalizationModal(product, event.currentTarget as HTMLElement);
    } else {
      this.cartService.addToCart(product, 1);
      // Directly trigger animation if no personalization needed
      this.triggerFlyAnimation(event.currentTarget as HTMLElement);
    }
  }

  // Store the button element that triggered the modal
  private triggerElementForModal: HTMLElement | null = null;

  openPersonalizationModal(product: Product, triggerElement?: HTMLElement) {
    this.selectedProduct = product;
    // Store the element that opened the modal (likely the 'Add to Cart' button)
    this.triggerElementForModal = triggerElement || null;
    this.showPersonalizationModal = true;
  }

  closePersonalizationModal() {
    this.showPersonalizationModal = false;
    this.selectedProduct = null;
    this.triggerElementForModal = null; // Clear the trigger element
  }

  onPersonalizationComplete(result: { goldBorder?: boolean, customImage?: File }) {
    const selectedProduct: Product | null = this.selectedProduct;
    const triggerElement: HTMLElement | null = this.triggerElementForModal;
    if (!selectedProduct?.id) {
      this.closePersonalizationModal();
      return;
    }

    this.productService.getProduct(selectedProduct.id).subscribe({
      next: (currentProduct: Product): void => {
        if (currentProduct.active === false || currentProduct.stockQuantity <= 0) {
          this.closePersonalizationModal();
          return;
        }
        this.cartService.addToCart(currentProduct, 1, result.goldBorder, result.customImage);
        if (triggerElement) {
          this.triggerFlyAnimation(triggerElement);
        }
        this.closePersonalizationModal();
      },
      error: (): void => this.closePersonalizationModal(),
    });
  }

  public triggerFlyAnimation(clickedElement: HTMLElement): void {
    const productCard = clickedElement.closest('.product-card');
    if (!productCard) {
      reportError('product-loading');
      return;
    }

    const productImage = productCard.querySelector('img');
    if (!productImage) {
      reportError('product-loading');
      return;
    }

    // Find the cart container using its class
    const cartContainer = document.querySelector('.cart-container'); // <--- CHANGE HERE
    if (!cartContainer) {
      reportError('product-loading');
      return; // Cart container not found
    }

    const imgRect = productImage.getBoundingClientRect();
    // Get the bounding rectangle of the container div
    const cartRect = cartContainer.getBoundingClientRect(); // <--- Use the container's rect

    // Create a clone of the image
    const imgClone = productImage.cloneNode(true) as HTMLImageElement;

    // Style the clone (same as before)
    this.renderer.setStyle(imgClone, 'position', 'fixed');
    this.renderer.setStyle(imgClone, 'top', `${imgRect.top}px`);
    this.renderer.setStyle(imgClone, 'left', `${imgRect.left}px`);
    this.renderer.setStyle(imgClone, 'width', `${imgRect.width}px`);
    this.renderer.setStyle(imgClone, 'height', `${imgRect.height}px`);
    this.renderer.setStyle(imgClone, 'opacity', '0.8');
    this.renderer.setStyle(imgClone, 'zIndex', '1000');
    this.renderer.setStyle(imgClone, 'borderRadius', '50%');
    this.renderer.setStyle(imgClone, 'transition', 'all 0.7s ease-in-out');
    this.renderer.setStyle(imgClone, 'pointerEvents', 'none');

    // Append the clone to the body
    this.renderer.appendChild(document.body, imgClone);

    // Force reflow
    imgClone.offsetWidth;

    // Calculate target position (center of the cart container)
    const targetX = cartRect.left + cartRect.width / 2 - imgRect.width / 2; // Adjust for clone width
    const targetY = cartRect.top + cartRect.height / 2 - imgRect.height / 2; // Adjust for clone height

    // Apply final animation styles (triggering the transition)
    this.renderer.setStyle(imgClone, 'top', `${targetY}px`);
    this.renderer.setStyle(imgClone, 'left', `${targetX}px`);
    this.renderer.setStyle(imgClone, 'width', `20px`);
    this.renderer.setStyle(imgClone, 'height', `20px`);
    this.renderer.setStyle(imgClone, 'opacity', '0');

    // Remove the clone after the animation duration
    setTimeout(() => {
      if (imgClone.parentNode === document.body) { // Check if it's still attached
        this.renderer.removeChild(document.body, imgClone);
      }
    }, 700); // Match the transition duration
  }
}
