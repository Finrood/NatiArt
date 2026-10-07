import {ImageCollection, ImageLoaderService, EMPTY_PRODUCT_IMAGE} from '../../../service/image-loader.service';
import {ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {BehaviorSubject, combineLatest, Observable, of, Subject} from 'rxjs';
import {catchError, finalize, map, startWith, takeUntil, tap} from 'rxjs/operators';
import {Router, RouterLink} from '@angular/router';
import { AsyncPipe, CurrencyPipe } from "@angular/common";
import {ShippingEstimationComponent} from "../shipping-estimation/shipping-estimation.component";
import {
  ConfirmationModalComponent
} from "../../../../shared/components/shared/confirmation-modal/confirmation-modal.component";
import {LoadingSpinnerComponent} from "../../../../shared/components/shared/loading-spinner/loading-spinner.component";
import {ButtonComponent} from "../../../../shared/components/button.component";
import {CartItem} from "../../../models/CartItem.model";
import {CartService} from "../../../service/cart.service";
import {reportError} from '../../../../shared/service/error-reporting.service';

interface CartState {
  items: CartItem[];
  total: number;
  isEmpty: boolean;
}

@Component({
  selector: 'app-cart',
  imports: [
    ConfirmationModalComponent,
    RouterLink,
    AsyncPipe,
    CurrencyPipe,
    ShippingEstimationComponent,
    LoadingSpinnerComponent,
    ButtonComponent
],
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CartComponent implements OnInit, OnDestroy {
  readonly images: ImageCollection = inject(ImageLoaderService).create();
  readonly emptyImage: string = EMPTY_PRODUCT_IMAGE;
  get imageUrls(): Record<string, string> { return this.images.urls(); }

  cartState$: Observable<CartState>;
  isLoading$ = new BehaviorSubject<boolean>(false);
  error$ = new BehaviorSubject<string | null>(null);

  @ViewChild(ConfirmationModalComponent) confirmationModal!: ConfirmationModalComponent;
  modalAction: (() => void) | null = null;

  private destroy$ = new Subject<void>();

  private readonly _cartService: CartService = inject(CartService);
  private readonly _router: Router = inject(Router);

  constructor() {
    this.cartState$ = combineLatest([
      this._cartService.getCartItems(),
      this._cartService.getCartTotal()
    ]).pipe(
      map(([items, total]) => ({
        items,
        total,
        isEmpty: items.length === 0
      })),
      startWith({ items: [], total: 0, isEmpty: true }),
      tap(state => this.prepareImageUrls(state.items)), // Prepare images when items change
      takeUntil(this.destroy$) // Manage subscription
    );
  }

  ngOnInit(): void {
    // Initial image prep happens in the observable pipeline
  }

  ngOnDestroy(): void {
    this.clearErrorDismissTimer();
    this.destroy$.next();
    this.destroy$.complete();
    this.images.destroy();
  }

  updateQuantity(item: CartItem, change: number): void {
    const newQuantity = Math.max(1, Math.min(item.quantity + change, item.product.stockQuantity));
    if (newQuantity !== item.quantity) {
      this.performAction(
        () => this._cartService.updateItemQuantity(item.cartItemId, newQuantity), // Use cartItemId
        $localize`Failed to update quantity. Please try again.`
      );
    }
  }

  askRemoveItem(item: CartItem): void {
    this.modalAction = () => this.performAction(
      () => this._cartService.removeFromCart(item.cartItemId), // Use cartItemId
      $localize`Failed to remove item. Please try again.`
    );
    this.confirmationModal.title = $localize`Remove Item`;
    this.confirmationModal.message = item.image || item.customImageUploadId
      ? $localize`:@@cartRemoveArtworkMessage:Remove "${item.product.label}:productName:" and its custom artwork from your cart?`
      : $localize`:@@cartRemoveMessage:Remove "${item.product.label}:productName:" from your cart?`;
    this.confirmationModal.confirmText = $localize`Remove`;
    this.confirmationModal.cancelText = $localize`Cancel`;
    this.confirmationModal.isOpen = true;
  }

  askClearCart(): void {
    this.modalAction = () => this.performAction(
      () => this._cartService.clearCart(),
      $localize`Failed to clear cart. Please try again.`
    );
    this.confirmationModal.title = $localize`Clear Cart`;
    this.confirmationModal.message = $localize`Are you sure you want to remove all items from your cart?`;
    this.confirmationModal.confirmText = $localize`Clear Cart`;
    this.confirmationModal.cancelText = $localize`Cancel`;
    this.confirmationModal.isOpen = true;
  }

  confirmModalAction(): void {
    if (this.modalAction) {
      this.modalAction();
    }
    this.closeModal();
  }

  closeModal(): void {
    if (this.confirmationModal) { // Check if modal exists
      this.confirmationModal.isOpen = false;
    }
    this.modalAction = null;
  }


  reselectArtwork(item: CartItem, event: Event): void {
    const file: File | undefined = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/') || file.size === 0 || file.size > 5_000_000) {
      this.setError($localize`Select an image smaller than 5 MB.`);
      return;
    }
    this._cartService.reselectArtwork(item.cartItemId, file);
  }

  proceedToCheckout(): void {
    this._router.navigate(['/checkout']).catch(error => {
      this.setError($localize`Failed to navigate to checkout. Please try again.`);
      reportError('cart-navigation', error);
    });
  }

  trackByCartItem(index: number, item: CartItem): string {
    return item.cartItemId; // Use unique cartItemId for tracking
  }

  // --- Private Helper Methods ---

  private performAction(action$: () => Observable<void>, errorMessage: string): void {
    this.isLoading$.next(true);
    this.setError(null);
    action$().pipe(
      catchError(error => {
        this.setError(errorMessage);
        reportError('cart', error);
        return of(null);
      }),
      finalize(() => this.isLoading$.next(false)),
      takeUntil(this.destroy$)
    ).subscribe();
  }

  private prepareImageUrls(items: CartItem[]): void {
    this.images.update(items.map((item: CartItem) => ({key: item.cartItemId, source: item.image || item.product.images?.[0]})));
  }


  private errorDismissTimer: ReturnType<typeof setTimeout> | undefined = undefined;

  protected setError(message: string | null): void {
    this.error$.next(message);
    this.clearErrorDismissTimer();
    if (message) {
      this.errorDismissTimer = setTimeout(() => this.error$.next(null), 5000);
    }
  }

  private clearErrorDismissTimer(): void {
    if (this.errorDismissTimer !== undefined) {
      clearTimeout(this.errorDismissTimer);
      this.errorDismissTimer = undefined;
    }
  }
}
