import {ImageCollection, ImageLoaderService, EMPTY_PRODUCT_IMAGE} from '../../../../service/image-loader.service';
import {Component, inject, Input, OnDestroy, OnInit, Renderer2} from '@angular/core';
import { AsyncPipe } from "@angular/common";
import {BehaviorSubject, Subscription} from "rxjs";
import {Product} from "../../../../models/product.model";
import {ProductService} from "../../../../service/product.service";
import {ProductCardComponent} from "../../../../../shared/components/product-card/product-card.component";
import {CartService} from "../../../../service/cart.service";
import {PersonalizationModalComponent} from "../../personalization-modal/personalization-modal.component";
import {PersonalizationOption} from "../../../../models/support/personalization-option";
import {reportError} from '../../../../../shared/service/error-reporting.service';

@Component({
  selector: 'app-product-list',
  imports: [AsyncPipe, ProductCardComponent, PersonalizationModalComponent],
  templateUrl: './product-list.component.html',
  styleUrls: ['./product-list.component.css']
})
export class ProductListComponent implements OnInit, OnDestroy {
  readonly images: ImageCollection = inject(ImageLoaderService).create();
  readonly emptyImage: string = EMPTY_PRODUCT_IMAGE;
  get imageUrls(): Record<string, string> { return this.images.urls(); }

  @Input() type: 'featured' | 'new' = 'featured';
  @Input() title: string = '';

  products = new BehaviorSubject<Product[]>([]);
  private subscriptions: Subscription[] = [];

  // Personalization modal
  showPersonalizationModal = false;
  selectedProduct: Product | null = null;

  private readonly _productService: ProductService = inject(ProductService);
  private readonly _cartService: CartService = inject(CartService);
  private readonly _renderer: Renderer2 = inject(Renderer2);


  ngOnInit(): void {
    this.getProducts();
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(sub => sub.unsubscribe());
    this.images.destroy();
  }

  private getProducts(): void {
    const productObservable = this.type === 'featured'
      ? this._productService.getFeaturedProducts()
      : this._productService.getNewProducts();
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
    this.images.update(products.map((product: Product, index: number) => ({key: product.id ?? 'card-' + index, source: product.id ? product.images?.[0] : undefined})));
  }

  addToCart(product: Product, event: MouseEvent) {
    const personalizations: PersonalizationOption[] = product.availablePersonalizations ?? [];
    if (personalizations.includes(PersonalizationOption.GOLDEN_BORDER) || personalizations.includes(PersonalizationOption.CUSTOM_IMAGE)) {
      // If personalization is needed, store the event target for later animation
      this.openPersonalizationModal(product, event.currentTarget as HTMLElement);
    } else {
      this._cartService.addToCart(product, 1);
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

    this._productService.getProduct(selectedProduct.id).subscribe({
      next: (currentProduct: Product): void => {
        if (currentProduct.active === false || currentProduct.stockQuantity <= 0) {
          this.closePersonalizationModal();
          return;
        }
        this._cartService.addToCart(currentProduct, 1, result.goldBorder, result.customImage);
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
    this._renderer.setStyle(imgClone, 'position', 'fixed');
    this._renderer.setStyle(imgClone, 'top', `${imgRect.top}px`);
    this._renderer.setStyle(imgClone, 'left', `${imgRect.left}px`);
    this._renderer.setStyle(imgClone, 'width', `${imgRect.width}px`);
    this._renderer.setStyle(imgClone, 'height', `${imgRect.height}px`);
    this._renderer.setStyle(imgClone, 'opacity', '0.8');
    this._renderer.setStyle(imgClone, 'zIndex', '1000');
    this._renderer.setStyle(imgClone, 'borderRadius', '50%');
    this._renderer.setStyle(imgClone, 'transition', 'all 0.7s ease-in-out');
    this._renderer.setStyle(imgClone, 'pointerEvents', 'none');

    // Append the clone to the body
    this._renderer.appendChild(document.body, imgClone);

    // Force reflow
    imgClone.offsetWidth;

    // Calculate target position (center of the cart container)
    const targetX = cartRect.left + cartRect.width / 2 - imgRect.width / 2; // Adjust for clone width
    const targetY = cartRect.top + cartRect.height / 2 - imgRect.height / 2; // Adjust for clone height

    // Apply final animation styles (triggering the transition)
    this._renderer.setStyle(imgClone, 'top', `${targetY}px`);
    this._renderer.setStyle(imgClone, 'left', `${targetX}px`);
    this._renderer.setStyle(imgClone, 'width', `20px`);
    this._renderer.setStyle(imgClone, 'height', `20px`);
    this._renderer.setStyle(imgClone, 'opacity', '0');

    // Remove the clone after the animation duration
    setTimeout(() => {
      if (imgClone.parentNode === document.body) { // Check if it's still attached
        this._renderer.removeChild(document.body, imgClone);
      }
    }, 700); // Match the transition duration
  }
}
