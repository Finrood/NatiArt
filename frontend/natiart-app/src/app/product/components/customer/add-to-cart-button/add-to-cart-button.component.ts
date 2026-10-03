import {Component, ElementRef, EventEmitter, Input, Output, inject} from '@angular/core';
import {PersonalizationModalComponent} from "../personalization-modal/personalization-modal.component";
import {Product} from "../../../models/product.model";
import {CartService} from "../../../service/cart.service";
import {ProductService} from "../../../service/product.service";
import {PersonalizationOption} from "../../../models/support/personalization-option";

import {ButtonComponent} from "../../../../shared/components/button.component";

@Component({
  selector: 'app-add-to-cart-button',
  imports: [PersonalizationModalComponent, ButtonComponent],
  template: `
    <app-button
      (click)="addToCartOrPersonalize(product, $event)"
      [disabled]="product.stockQuantity <= 0 || isAdding"
      [block]="true"
      color="primary"
      size="md"
    >
      @if (product.stockQuantity > 0) {
        @if (isAdding) { <span i18n>Adding...</span> }
        @else { <span i18n>Add to Cart</span> }
      } @else { <span i18n>Out of Stock</span> }
    </app-button>

    <app-personalization-modal
      (close)="closePersonalizationModal()"
      (personalize)="onPersonalizationComplete($event)"
      [product]="selectedProductForModal"
      [show]="showPersonalizationModal">
    </app-personalization-modal>
  `
})
export class AddToCartButtonComponent {
  @Input({ required: true }) product!: Product;
  @Input() quantity: number = 1; // Default quantity is 1
  @Output() itemAdded = new EventEmitter<HTMLElement>(); // Emit the button element

  showPersonalizationModal = false;
  selectedProductForModal: Product | null = null;
  isAdding = false; // Optional: for button state

  private readonly _cartService: CartService = inject(CartService);
  private readonly _productService: ProductService = inject(ProductService);
  private readonly _elRef: ElementRef<HTMLElement> = inject(ElementRef);

  constructor() {}

  addToCartOrPersonalize(product: Product, event: MouseEvent) {
    this.isAdding = true; // Set adding state
    const triggerElement = event.currentTarget as HTMLElement;

    const needsPersonalization = product.availablePersonalizations?.some(
      p => p === PersonalizationOption.GOLDEN_BORDER || p === PersonalizationOption.CUSTOM_IMAGE
    );

    if (needsPersonalization) {
      this.openPersonalizationModal(product);
      // isAdding will be reset in onPersonalizationComplete or closePersonalizationModal
    } else {
      this._cartService.addToCart(product, this.quantity);
      this.itemAdded.emit(this._elRef.nativeElement.querySelector('button')!); // Emit button element
      this.isAdding = false; // Reset adding state
    }
  }

  openPersonalizationModal(product: Product) {
    this.selectedProductForModal = product;
    this.showPersonalizationModal = true;
    // Don't reset isAdding here, wait for modal action
  }

  closePersonalizationModal() {
    this.showPersonalizationModal = false;
    this.selectedProductForModal = null;
    this.isAdding = false; // Reset adding state if modal is cancelled
  }

  onPersonalizationComplete(result: { goldBorder?: boolean, customImage?: File }) {
    const selectedProduct: Product | null = this.selectedProductForModal;
    if (!selectedProduct?.id) {
      this.closePersonalizationModal();
      return;
    }

    // The modal may stay open while a listing refresh changes price, stock, or
    // availability. Re-read the product before persisting the cart line so a
    // stale snapshot cannot become the checkout price.
    this._productService.getProduct(selectedProduct.id).subscribe({
      next: (currentProduct: Product): void => {
        const quantity: number = Math.min(this.quantity, currentProduct.stockQuantity);
        if (currentProduct.active === false || quantity <= 0) {
          this.closePersonalizationModal();
          return;
        }
        this._cartService.addToCart(currentProduct, quantity, result.goldBorder, result.customImage);
        this.itemAdded.emit(this._elRef.nativeElement.querySelector('button')!);
        this.closePersonalizationModal();
      },
      error: (): void => this.closePersonalizationModal(),
    });
  }
}
