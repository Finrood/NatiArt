import {Component, ElementRef, EventEmitter, Input, Output} from '@angular/core';
import {PersonalizationModalComponent} from "../personalization-modal/personalization-modal.component";
import {Product} from "../../../models/product.model";
import {CartService} from "../../../service/cart.service";
import {PersonalizationOption} from "../../../models/support/personalization-option";
import {CommonModule} from "@angular/common";

@Component({
  selector: 'app-add-to-cart-button',
  standalone: true,
  imports: [CommonModule, PersonalizationModalComponent, PersonalizationModalComponent],
  template: `
    <button
      (click)="addToCartOrPersonalize(product, $event)"
      [disabled]="product.stockQuantity <= 0 || isAdding"
      class="w-full bg-primary text-white px-4 py-2 rounded-full font-medium hover:bg-primary-dark transition-colors focus:outline-none focus:ring-2 focus:ring-primary focus:ring-opacity-50 disabled:opacity-50 disabled:cursor-not-allowed"
    >
      {{ product.stockQuantity > 0 ? (isAdding ? 'Adding...' : 'Add to Cart') : 'Out of Stock' }}
    </button>

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

  constructor(
    private cartService: CartService,
    private elRef: ElementRef<HTMLElement> // Inject ElementRef to get the button
  ) {}

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
      this.cartService.addToCart(product, this.quantity);
      this.itemAdded.emit(this.elRef.nativeElement.querySelector('button')!); // Emit button element
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
    if (this.selectedProductForModal) {
      this.cartService.addToCart(
        this.selectedProductForModal,
        this.quantity, // Use the quantity passed to the component
        result.goldBorder,
        result.customImage
      );
      this.itemAdded.emit(this.elRef.nativeElement.querySelector('button')!); // Emit button element
    }
    this.closePersonalizationModal(); // This will also reset isAdding
  }
}
