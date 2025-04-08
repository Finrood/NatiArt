import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {AsyncPipe, CurrencyPipe, NgForOf, NgIf} from "@angular/common";
import {BehaviorSubject, Subscription} from "rxjs";
import {Product} from "../../../../models/product.model";
import {ProductService} from "../../../../service/product.service";
import {RouterLink} from "@angular/router";
import {DomSanitizer, SafeUrl} from "@angular/platform-browser";
import {CartService} from "../../../../service/cart.service";

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [AsyncPipe, CurrencyPipe, NgForOf, NgIf, RouterLink],
  templateUrl: './product-list.component.html',
  styleUrls: ['./product-list.component.css']
})
export class ProductListComponent implements OnInit, OnDestroy {
  @Input() type: 'featured' | 'new' = 'featured';
  @Input() title: string = '';

  products = new BehaviorSubject<Product[]>([]);
  scrollPosition = 0;
  imageUrls: { [productId: string]: SafeUrl | null } = {};
  private subscriptions: Subscription[] = [];

  constructor(
    private productService: ProductService,
    private cartService: CartService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit() {
    this.getProducts();
  }

  ngOnDestroy() {
    this.subscriptions.forEach(sub => sub.unsubscribe());
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
      error: (error) => console.error(`Error getting ${this.type} products:`, error)
    });
    this.subscriptions.push(sub);
  }

  private updateProductImages(products: Product[]): void {
    products.forEach(product => {
      if (product.images && product.images.length > 0) {
        this.fetchImage(product.id!, product.images[0]);
      }
    });
  }

  private fetchImage(productId: string, imagePath: string): void {
    const sub = this.productService.getImage(imagePath).subscribe(blob => {
      const objectUrl = URL.createObjectURL(blob);
      this.imageUrls[productId] = this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl);
      this.products.next([...this.products.value]);
    });
    this.subscriptions.push(sub);
  }

  scrollProducts(direction: 'left' | 'right') {
    const containerWidth = window.innerWidth;
    const scrollAmount = containerWidth * (direction === 'left' ? -1 : 1) * 0.5;
    const maxScroll = (this.products.value.length - 2) * containerWidth / 3;
    this.scrollPosition = Math.max(0, Math.min(this.scrollPosition + scrollAmount, maxScroll));
  }

  isOriginalPriceHidden(product: Product): boolean {
    return product.originalPrice === product.markedPrice;
  }

  addToCart(product: Product) {
    this.cartService.addToCart(product, 1);
  }
}
