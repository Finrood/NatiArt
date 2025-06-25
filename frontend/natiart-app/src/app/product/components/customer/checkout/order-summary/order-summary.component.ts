import {ChangeDetectionStrategy, Component, Input, OnDestroy, OnInit} from '@angular/core';
import {CurrencyPipe, NgForOf, NgIf} from "@angular/common";
import {CartItem} from "../../../../models/CartItem.model";
import {DomSanitizer, SafeUrl} from "@angular/platform-browser";
import {ProductService} from "../../../../service/product.service";
import {Subject} from "rxjs";
import {takeUntil} from "rxjs/operators";
import {RouterLink} from "@angular/router";

@Component({
  selector: 'app-order-summary',
  standalone: true,
  imports: [
    CurrencyPipe,
    NgForOf,
    NgIf,
    RouterLink
  ],
  templateUrl: './order-summary.component.html',
  // styleUrls: ['./order-summary.component.css'] // Keep if you have specific styles
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OrderSummaryComponent implements OnInit, OnDestroy {
  @Input() cartItems: CartItem[] | null = null;
  @Input() cartTotal: number | null = 0;

  imageUrls: { [cartItemId: string]: SafeUrl | string } = {};
  private objectUrlsCreated: string[] = [];
  private destroy$ = new Subject<void>();

  constructor(
    private productService: ProductService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    if (this.cartItems) {
      this.prepareImageUrls(this.cartItems);
    }
  }

  ngOnChanges(): void { // Detect changes to cartItems input
    if (this.cartItems) {
      this.prepareImageUrls(this.cartItems);
    }
  }

  private prepareImageUrls(items: CartItem[]): void {
    const currentItemIds = new Set(items.map(item => item.cartItemId));
    Object.keys(this.imageUrls).forEach(cartItemId => {
      if (!currentItemIds.has(cartItemId)) {
        const url = this.imageUrls[cartItemId];
        if (typeof url === 'string' && url.startsWith('blob:')) {
          URL.revokeObjectURL(url);
          const index = this.objectUrlsCreated.indexOf(url);
          if (index > -1) this.objectUrlsCreated.splice(index, 1);
        }
        delete this.imageUrls[cartItemId];
      }
    });

    items.forEach(item => {
      if (!this.imageUrls[item.cartItemId]) {
        if (item.image instanceof File) {
          const objectUrl = URL.createObjectURL(item.image);
          this.imageUrls[item.cartItemId] = this.sanitizer.bypassSecurityTrustUrl(objectUrl);
          this.objectUrlsCreated.push(objectUrl);
        } else if (item.product.images && item.product.images.length > 0) {
          this.fetchProductImage(item.cartItemId, item.product.images[0]);
        } else {
          this.imageUrls[item.cartItemId] = 'assets/img/placeholder.png';
        }
      }
    });
  }

  private fetchProductImage(cartItemId: string, imagePath: string): void {
    this.productService.getImage(imagePath).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: blob => {
        const objectUrl = URL.createObjectURL(blob);
        this.imageUrls[cartItemId] = this.sanitizer.bypassSecurityTrustUrl(objectUrl);
        this.objectUrlsCreated.push(objectUrl);
      },
      error: () => {
        this.imageUrls[cartItemId] = 'assets/img/placeholder.png';
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.objectUrlsCreated.forEach(url => URL.revokeObjectURL(url));
  }
}
