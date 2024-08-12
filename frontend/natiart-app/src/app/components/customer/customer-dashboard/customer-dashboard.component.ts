import {Component} from '@angular/core';
import {TopMenuComponent} from "../top-menu/top-menu.component";
import {LeftMenuComponent} from "../left-menu/left-menu.component";
import {AsyncPipe, CurrencyPipe, NgForOf, NgIf} from "@angular/common";
import {Product} from "../../../models/product.model";
import {ProductService} from "../../../service/product.service";
import {BehaviorSubject, Subscription} from "rxjs";
import {DomSanitizer, SafeUrl} from "@angular/platform-browser";
import {RouterLink} from "@angular/router";
import {CartService} from "../../../service/cart.service";
import {PersonalizationModalComponent} from "../personalization-modal/personalization-modal.component";

@Component({
  selector: 'app-customer-dashboard',
  standalone: true,
  imports: [
    TopMenuComponent,
    LeftMenuComponent,
    NgForOf,
    NgIf,
    AsyncPipe,
    RouterLink,
    CurrencyPipe,
    PersonalizationModalComponent
  ],
  templateUrl: './customer-dashboard.component.html',
  styleUrl: './customer-dashboard.component.css'
})
export class CustomerDashboardComponent {
  currentBannerIndex: number = 0;
  private bannerInterval: any;
  private readonly SLIDE_DURATION: number = 3500;
  private readonly TRANSITION_DURATION: number = 1000;
  bannerImages: string[] = [
    "assets/img/a1.webp",
    "assets/img/a2.jpg",
    "assets/img/a3.jpg",
    "assets/img/a4.jpg"

  ];
  featuredProducts = new BehaviorSubject<Product[]>([]);
  newProducts = new BehaviorSubject<Product[]>([]);

  featuredScrollPosition = 0;
  newProductsScrollPosition = 0;

  imageUrls: { [productId: string]: SafeUrl | null } = {};
  private subscriptions: Subscription[] = [];

  showPersonalizationModal = false;
  selectedProduct: Product | null = null;

  constructor(
    private productService: ProductService,
    private cartService: CartService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit() {
    this.startBannerInterval();
    this.getFeaturedProducts();
    this.getNewProducts();
  }

  ngOnDestroy() {
    if (this.bannerInterval) {
      clearInterval(this.bannerInterval);
    }
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
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
    if (product.canPersonaliseGold || product.canPersonaliseImage) {
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

  private startBannerInterval() {
    this.stopBannerInterval(); // Ensure any existing interval is stopped
    this.bannerInterval = setInterval(() => {
      this.nextSlide();
    }, this.SLIDE_DURATION + this.TRANSITION_DURATION);
  }

  private stopBannerInterval() {
    if (this.bannerInterval) {
      clearInterval(this.bannerInterval);
    }
  }

  prevSlide() {
    this.currentBannerIndex = (this.currentBannerIndex - 1 + this.bannerImages.length) % this.bannerImages.length;
    this.resetBannerInterval();
  }

  nextSlide() {
    this.currentBannerIndex = (this.currentBannerIndex + 1) % this.bannerImages.length;
    this.resetBannerInterval();
  }

  goToSlide(index: number) {
    this.currentBannerIndex = index;
    this.resetBannerInterval();
  }

  private resetBannerInterval() {
    this.startBannerInterval();
  }
}
