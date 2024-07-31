import { Component, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { Observable, BehaviorSubject, Subject, combineLatest } from 'rxjs';
import { map, switchMap, takeUntil, catchError, finalize, startWith } from 'rxjs/operators';
import { CartItem } from '../../../models/CartItem.model';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { CartService } from '../../../service/cart.service';
import { ProductService } from '../../../service/product.service';
import {Router, RouterLink} from '@angular/router';
import {ConfirmationModalComponent} from "../../shared/confirmation-modal/confirmation-modal.component";
import {AsyncPipe, CurrencyPipe, NgForOf, NgIf} from "@angular/common";
import {ShippingEstimationComponent} from "../shipping-estimation/shipping-estimation.component";

@Component({
  selector: 'app-cart',
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.css'],
  standalone: true,
  imports: [
    ConfirmationModalComponent,
    RouterLink,
    NgIf,
    AsyncPipe,
    CurrencyPipe,
    ShippingEstimationComponent,
    NgForOf
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CartComponent implements OnInit, OnDestroy {
  cartState$: Observable<{
    items: CartItem[];
    total: number;
    isEmpty: boolean;
  }>;
  imageUrls: { [productId: string]: SafeUrl } = {};
  isLoading$ = new BehaviorSubject<boolean>(false);
  error$ = new BehaviorSubject<string | null>(null);
  private destroy$ = new Subject<void>();

  modalConfig = {
    isOpen: false,
    title: '',
    message: '',
    confirmAction: () => {}
  };

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
      startWith({ items: [], total: 0, isEmpty: true })
    );
  }

  ngOnInit(): void {
    this.loadProductImages();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  updateQuantity(item: CartItem, change: number): void {
    const newQuantity = Math.max(1, Math.min(item.quantity + change, item.product.stockQuantity));
    if (newQuantity !== item.quantity) {
      this.performAction(() => this.cartService.updateItemQuantity(item.product.id!, newQuantity),
        'Failed to update quantity. Please try again.');
    }
  }

  removeItem(item: CartItem): void {
    this.openModal('Remove Item', `Are you sure you want to remove "${item.product.label}" from your cart?`,
      () => this.performAction(() => this.cartService.removeFromCart(item.product.id!),
        'Failed to remove item. Please try again.'));
  }

  clearCart(): void {
    this.openModal('Clear Cart', 'Are you sure you want to remove all items from your cart?',
      () => this.performAction(() => this.cartService.clearCart(),
        'Failed to clear cart. Please try again.'));
  }

  proceedToCheckout(): void {
    this.router.navigate(['/checkout']).catch(error => {
      this.error$.next('Failed to navigate to checkout. Please try again.');
      console.error('Error navigating to checkout:', error);
    });
  }

  private openModal(title: string, message: string, confirmAction: () => void): void {
    this.modalConfig = { isOpen: true, title, message, confirmAction };
  }

  confirmModal(): void {
    this.modalConfig.isOpen = false;
    this.modalConfig.confirmAction();
  }

  cancelModal(): void {
    this.modalConfig.isOpen = false;
  }

  private performAction(action: () => Observable<any>, errorMessage: string): void {
    this.isLoading$.next(true);
    action().pipe(
      catchError(error => {
        this.error$.next(errorMessage);
        console.error('Error:', error);
        return [];
      }),
      finalize(() => this.isLoading$.next(false)),
      takeUntil(this.destroy$)
    ).subscribe();
  }

  private loadProductImages(): void {
    this.cartState$.pipe(
      switchMap(state => state.items.map(item => this.fetchImage(item.product.id!, item.product.images[0]))),
      takeUntil(this.destroy$)
    ).subscribe();
  }

  private fetchImage(productId: string, imagePath: string): Observable<void> {
    return this.productService.getImage(imagePath).pipe(
      map(blob => {
        const objectUrl = URL.createObjectURL(blob);
        this.imageUrls[productId] = this.sanitizer.bypassSecurityTrustUrl(objectUrl);
      }),
      catchError(error => {
        console.error(`Failed to load image for product ${productId}:`, error);
        return [];
      })
    );
  }
}
