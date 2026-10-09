import {Component, DestroyRef, ElementRef, EventEmitter, Input, Output, inject, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Subscription, finalize} from 'rxjs';
import {PersonalizationModalComponent} from '../personalization-modal/personalization-modal.component';
import {Product} from '../../../models/product.model';
import {CartItem} from '../../../models/CartItem.model';
import {CartService} from '../../../service/cart.service';
import {ProductService} from '../../../service/product.service';
import {PersonalizationOption} from '../../../models/support/personalization-option';
import {ButtonComponent} from '../../../../shared/components/button.component';

@Component({
  selector: 'app-add-to-cart-button',
  imports: [PersonalizationModalComponent, ButtonComponent, RouterLink],
  template: `
    <app-button (click)="addToCartOrPersonalize(product, $event)"
      [disabled]="product.stockQuantity <= 0 || !validQuantity || isAdding" [block]="true" color="primary">
      @if (product.stockQuantity <= 0) { <span i18n>Out of Stock</span> }
      @else if (isAdding) { <span i18n>Adding...</span> }
      @else if (needsPersonalization) { <span i18n>Choose options</span> }
      @else { <span i18n>Add to Cart</span> }
    </app-button>
    <p role="status" class="text-sm text-success mt-2">
      @if ($added()) { <span i18n>Added to cart.</span> <a routerLink="/cart" class="art-link" i18n>View cart</a> }
    </p>
    @if ($error() && !showPersonalizationModal) { <p role="alert" class="text-error text-sm mt-2">{{ $error() }}</p> }
    <app-personalization-modal (close)="closePersonalizationModal()" (personalize)="onPersonalizationComplete($event)"
      [product]="selectedProductForModal" [show]="showPersonalizationModal" [closeOnSubmit]="false"
      [pending]="$checkingOptions()" [errorMessage]="$error()">
    </app-personalization-modal>
  `
})
export class AddToCartButtonComponent {
  @Input({required: true}) product!: Product;
  @Input() quantity: number = 1;
  @Output() itemAdded = new EventEmitter<HTMLElement>();
  readonly $added = signal(false);
  readonly $error = signal('');
  readonly $checkingOptions = signal(false);
  readonly $modalOpen = signal(false);
  get showPersonalizationModal(): boolean { return this.$modalOpen(); }
  get isAdding(): boolean { return this.$modalOpen() || this.$checkingOptions(); }
  selectedProductForModal: Product | null = null;
  private optionsRequest: Subscription | null = null;
  private readonly _cartService: CartService = inject(CartService);
  private readonly _productService: ProductService = inject(ProductService);
  private readonly _elRef: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly _destroyRef: DestroyRef = inject(DestroyRef);

  get validQuantity(): boolean {
    return Number.isSafeInteger(this.quantity) && this.quantity >= 1
      && this.quantity <= Math.min(this.product.stockQuantity, 100);
  }

  get needsPersonalization(): boolean {
    return this.product.availablePersonalizations?.some((option: PersonalizationOption): boolean =>
      option === PersonalizationOption.GOLDEN_BORDER || option === PersonalizationOption.CUSTOM_IMAGE) ?? false;
  }

  addToCartOrPersonalize(product: Product, _event: MouseEvent): void {
    if (this.isAdding || product.stockQuantity <= 0) return;
    this.$added.set(false);
    this.$error.set('');
    if (!this.validQuantity) {
      this.$error.set($localize`Choose a whole-number quantity within the available stock.`);
      return;
    }
    if (this.needsPersonalization) this.openPersonalizationModal(product);
    else this.addProduct(product, this.quantity);
  }

  openPersonalizationModal(product: Product): void {
    this.selectedProductForModal = product;
    this.$modalOpen.set(true);
  }

  closePersonalizationModal(): void {
    this.optionsRequest?.unsubscribe();
    this.optionsRequest = null;
    this.$modalOpen.set(false);
    this.selectedProductForModal = null;
  }

  onPersonalizationComplete(result: {goldBorder?: boolean; customImage?: File}): void {
    if (this.$checkingOptions()) return;
    const selectedProduct: Product | null = this.selectedProductForModal;
    if (!selectedProduct?.id) { this.closePersonalizationModal(); return; }
    this.$checkingOptions.set(true);
    this.$error.set('');
    // Keep the dialog and selected file until the current product has been checked and the line accepted.
    this.optionsRequest = this._productService.getProduct(selectedProduct.id).pipe(
      takeUntilDestroyed(this._destroyRef), finalize((): void => this.$checkingOptions.set(false))
    ).subscribe({
      next: (currentProduct: Product): void => {
        if (currentProduct.active === false || currentProduct.stockQuantity <= 0) {
          this.$error.set($localize`This piece is no longer available. Choose another piece from Collections.`);
          return;
        }
        if (this.addProduct(currentProduct, this.quantity, result.goldBorder, result.customImage)) {
          this.closePersonalizationModal();
        }
      },
      error: (): void => this.$error.set($localize`We could not check this piece. Your options are kept. Please try again.`)
    });
  }

  private addProduct(product: Product, quantity: number, goldBorder?: boolean, image?: File): boolean {
    const count = (): number => this._cartService.getCartItemsSnapshot()
      .filter((item: CartItem): boolean => item.product.id === product.id)
      .reduce((total: number, item: CartItem): number => total + item.quantity, 0);
    const previous: number = count();
    if (!Number.isSafeInteger(quantity) || quantity < 1
      || quantity + previous > Math.min(product.stockQuantity, 100)) {
      this.$error.set($localize`This quantity could not be added. Review your cart to adjust it.`);
      return false;
    }
    this._cartService.addToCart(product, quantity, goldBorder, image);
    if (count() <= previous) {
      this.$error.set($localize`This quantity could not be added. Review your cart to adjust it.`);
      return false;
    }
    this.$added.set(true);
    const button: HTMLButtonElement | null = this._elRef.nativeElement.querySelector('button');
    if (button) this.itemAdded.emit(button);
    return true;
  }
}
