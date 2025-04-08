import {Component} from '@angular/core';
import {TopMenuComponent} from "../top-menu/top-menu.component";
import {LeftMenuComponent} from "../left-menu/left-menu.component";
import {Product} from "../../../models/product.model";
import {ProductService} from "../../../service/product.service";
import {BehaviorSubject, Subscription} from "rxjs";
import {DomSanitizer, SafeUrl} from "@angular/platform-browser";
import {CartService} from "../../../service/cart.service";
import {PersonalizationModalComponent} from "../personalization-modal/personalization-modal.component";
import {PersonalizationOption} from "../../../models/support/personalization-option";
import {TopBannerComponent} from "../dashboard/top-banner/top-banner.component";
import {ProductListComponent} from "../dashboard/product-list/product-list.component";

@Component({
    selector: 'app-customer-dashboard',
  imports: [
    TopMenuComponent,
    LeftMenuComponent,
    PersonalizationModalComponent,
    TopBannerComponent,
    ProductListComponent
  ],
    templateUrl: './customer-dashboard.component.html',
    styleUrl: './customer-dashboard.component.css'
})
export class CustomerDashboardComponent {
  featuredProducts = new BehaviorSubject<Product[]>([]);
  newProducts = new BehaviorSubject<Product[]>([]);
  featuredScrollPosition = 0;
  newProductsScrollPosition = 0;
  imageUrls: { [productId: string]: SafeUrl | null } = {};
  showPersonalizationModal = false;
  selectedProduct: Product | null = null;
  private subscriptions: Subscription[] = [];

  constructor(
    private productService: ProductService,
    private cartService: CartService,
    private sanitizer: DomSanitizer
  ) {
  }

  isOriginalPriceHidden(product: Product): boolean {
    return product.originalPrice == product.markedPrice;
  }

  scrollProducts(section: 'featured' | 'new', direction: 'left' | 'right') {
    const containerWidth = window.innerWidth;
    const scrollAmount = containerWidth * (direction === 'left' ? -1 : 1);

    if (section === 'featured') {
      this.featuredScrollPosition = Math.max(0, Math.min(this.featuredScrollPosition + scrollAmount, (this.featuredProducts.value.length - 3) * containerWidth / 3));
    } else {
      this.newProductsScrollPosition = Math.max(0, Math.min(this.newProductsScrollPosition + scrollAmount, (this.newProducts.value.length - 3) * containerWidth / 3));
    }
  }

  addToCart(product: Product) {
    if (product.availablePersonalizations.includes(PersonalizationOption.GOLDEN_BORDER) || product.availablePersonalizations.includes(PersonalizationOption.CUSTOM_IMAGE)) {
      this.openPersonalizationModal(product);
    } else {
      this.cartService.addToCart(product, 1);
    }
  }

  openPersonalizationModal(product: Product) {
    this.selectedProduct = product;
    this.showPersonalizationModal = true;
  }

  closePersonalizationModal() {
    this.showPersonalizationModal = false;
    this.selectedProduct = null;
  }

  onPersonalizationComplete(result: { goldBorder?: boolean, customImage?: File }) {
    if (this.selectedProduct) {
      this.cartService.addToCart(this.selectedProduct, 1, result.goldBorder, result.customImage);
    }
    this.closePersonalizationModal();
  }

  private getFeaturedProducts(): void {
    this.productService.getFeaturedProducts().subscribe({
      next: (response) => {
        this.featuredProducts.next(response);
        this.updateProductImages(response);
      },
      error: (error) => console.error('Error getting featured products:', error)
    });
  }

  private getNewProducts(): void {
    this.productService.getNewProducts().subscribe({
      next: (response) => {
        this.newProducts.next(response);
        this.updateProductImages(response);
      },
      error: (error) => console.error('Error getting new products:', error)
    });
  }

  private updateProductImages(products: Product[]): void {
    products.forEach(product => {
      if (product.images && product.images.length > 0) {
        this.fetchImage(product.id!, product.images[0]);
      }
    });
  }

  private fetchImage(productId: string, imagePath: string): void {
    const subscription = this.productService.getImage(imagePath).subscribe(blob => {
      const objectUrl = URL.createObjectURL(blob);
      this.imageUrls[productId] = this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl);
      this.featuredProducts.next([...this.featuredProducts.value]);
      this.newProducts.next([...this.newProducts.value]);
    });
    this.subscriptions.push(subscription);
  }
}
