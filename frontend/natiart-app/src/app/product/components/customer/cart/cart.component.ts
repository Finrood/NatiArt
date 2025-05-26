// START OF FILE: src/app/product/components/customer/cart/cart.component.ts
import {ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild} from '@angular/core'; // Import SecurityContext
import {BehaviorSubject, combineLatest, Observable, of, Subject} from 'rxjs';
import {catchError, finalize, map, startWith, takeUntil, tap} from 'rxjs/operators';
import {DomSanitizer, SafeUrl} from '@angular/platform-browser';
import {Router, RouterLink} from '@angular/router';
import {AsyncPipe, CurrencyPipe, NgForOf, NgIf} from "@angular/common";
import {ShippingEstimationComponent} from "../shipping-estimation/shipping-estimation.component";
import {
  ConfirmationModalComponent
} from "../../../../shared/components/shared/confirmation-modal/confirmation-modal.component";
import {LoadingSpinnerComponent} from "../../../../shared/components/shared/loading-spinner/loading-spinner.component";
import {CartItem} from "../../../models/CartItem.model";
import {CartService} from "../../../service/cart.service";
import {ProductService} from "../../../service/product.service";

interface CartState {
  items: CartItem[];
  total: number;
  isEmpty: boolean;
}

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [
    ConfirmationModalComponent,
    RouterLink,
    NgIf,
    AsyncPipe,
    CurrencyPipe,
    ShippingEstimationComponent,
    NgForOf,
    LoadingSpinnerComponent
  ],
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CartComponent implements OnInit, OnDestroy {
  cartState$: Observable<CartState>;
  // Use cartItemId as key. Store SafeUrl or placeholder string.
  imageUrls: { [cartItemId: string]: SafeUrl | string } = {};
  isLoading$ = new BehaviorSubject<boolean>(false);
  error$ = new BehaviorSubject<string | null>(null);

  @ViewChild(ConfirmationModalComponent) confirmationModal!: ConfirmationModalComponent;
  modalAction: (() => void) | null = null;

  private objectUrlsCreated: string[] = []; // Keep track of created blob URLs
  private destroy$ = new Subject<void>();

  constructor(
    private cartService: CartService,
    private productService: ProductService,
    private sanitizer: DomSanitizer,
    private router: Router
  ) {
    this.cartState$ = combineLatest([
      this.cartService.getCartItems(),
      this.cartService.getCartTotal()
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
    this.destroy$.next();
    this.destroy$.complete();
    // Clean up ALL previously created object URLs
    this.objectUrlsCreated.forEach(url => URL.revokeObjectURL(url));
    this.objectUrlsCreated = []; // Clear the tracking array
    console.log("CartComponent destroyed, Object URLs revoked.");
  }

  updateQuantity(item: CartItem, change: number): void {
    const newQuantity = Math.max(1, Math.min(item.quantity + change, item.product.stockQuantity));
    if (newQuantity !== item.quantity) {
      this.performAction(
        () => this.cartService.updateItemQuantity(item.cartItemId, newQuantity), // Use cartItemId
        'Failed to update quantity. Please try again.'
      );
    }
  }

  askRemoveItem(item: CartItem): void {
    this.modalAction = () => this.performAction(
      () => this.cartService.removeFromCart(item.cartItemId), // Use cartItemId
      'Failed to remove item. Please try again.'
    );
    this.confirmationModal.title = 'Remove Item';
    this.confirmationModal.message = `Are you sure you want to remove this instance of "${item.product.label}"${item.image ? ' (with custom image)' : ''} from your cart?`;
    this.confirmationModal.confirmText = 'Remove';
    this.confirmationModal.cancelText = 'Cancel';
    this.confirmationModal.isOpen = true;
  }

  askClearCart(): void {
    this.modalAction = () => this.performAction(
      () => this.cartService.clearCart(),
      'Failed to clear cart. Please try again.'
    );
    this.confirmationModal.title = 'Clear Cart';
    this.confirmationModal.message = 'Are you sure you want to remove all items from your cart?';
    this.confirmationModal.confirmText = 'Clear Cart';
    this.confirmationModal.cancelText = 'Cancel';
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


  proceedToCheckout(): void {
    this.router.navigate(['/checkout']).catch(error => {
      this.setError('Failed to navigate to checkout. Please try again.');
      console.error('Error navigating to checkout:', error);
    });
  }

  trackByCartItem(index: number, item: CartItem): string {
    return item.cartItemId; // Use unique cartItemId for tracking
  }

  // --- Private Helper Methods ---

  private performAction(action$: () => Observable<any>, errorMessage: string): void {
    this.isLoading$.next(true);
    this.setError(null);
    action$().pipe(
      catchError(error => {
        this.setError(errorMessage);
        console.error('Cart action error:', error);
        return of(null);
      }),
      finalize(() => this.isLoading$.next(false)),
      takeUntil(this.destroy$)
    ).subscribe();
  }

  private prepareImageUrls(items: CartItem[]): void {
    // Revoke URLs for items no longer in the cart
    const currentItemIds = new Set(items.map(item => item.cartItemId));
    const urlsToRemove = Object.keys(this.imageUrls).filter(id => !currentItemIds.has(id));
    urlsToRemove.forEach(cartItemId => {
      const url = this.imageUrls[cartItemId];
      if (typeof url === 'string' && url.startsWith('blob:')) {
        URL.revokeObjectURL(url);
        const index = this.objectUrlsCreated.indexOf(url);
        if (index > -1) this.objectUrlsCreated.splice(index, 1);
      }
      delete this.imageUrls[cartItemId]; // Remove from map
    });


    // Create/fetch URLs for current items if needed
    items.forEach(item => {
      if (!this.imageUrls[item.cartItemId]) { // Only process if URL doesn't exist
        if (item.image instanceof File) {
          // Create Object URL for the custom File image
          const objectUrl = URL.createObjectURL(item.image);
          this.imageUrls[item.cartItemId] = this.sanitizer.bypassSecurityTrustUrl(objectUrl);
          this.objectUrlsCreated.push(objectUrl); // Track for cleanup
        } else if (item.product.images && item.product.images.length > 0) {
          // Fetch the default product image if no custom image
          this.fetchProductImage(item.cartItemId, item.product.images[0]);
        } else {
          // Use placeholder if no images available
          this.imageUrls[item.cartItemId] = 'assets/img/placeholder.png';
        }
      }
    });
  }

  private fetchProductImage(cartItemId: string, imagePath: string): void {
    // Indicate loading state maybe?
    // this.imageUrls[cartItemId] = 'loading'; // Or some indicator URL

    this.productService.getImage(imagePath).pipe(
      takeUntil(this.destroy$) // Auto-unsubscribe
    ).subscribe({
      next: blob => {
        const objectUrl = URL.createObjectURL(blob);
        this.imageUrls[cartItemId] = this.sanitizer.bypassSecurityTrustUrl(objectUrl);
        this.objectUrlsCreated.push(objectUrl); // Track for cleanup
      },
      error: error => {
        console.error(`Failed to load product image for cart item ${cartItemId}:`, error);
        this.imageUrls[cartItemId] = 'assets/img/placeholder.png'; // Fallback
      }
    });
  }

  protected setError(message: string | null): void {
    this.error$.next(message);
    if (message) {
      setTimeout(() => this.error$.next(null), 5000);
    }
  }
}
