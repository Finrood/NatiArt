import {Component, OnInit, OnDestroy, ViewChild, ElementRef, Renderer2} from '@angular/core';
import {AsyncPipe, NgForOf, NgIf, CurrencyPipe, KeyValuePipe, NgStyle} from "@angular/common";
import { FormsModule } from "@angular/forms";
import { Observable, BehaviorSubject, Subscription } from "rxjs";
import { Product } from "../../../models/product.model";
import { ActivatedRoute } from "@angular/router";
import { ProductService } from "../../../service/product.service";
import { DomSanitizer, SafeUrl } from "@angular/platform-browser";
import { TopMenuComponent } from "../top-menu/top-menu.component";
import { LeftMenuComponent } from "../left-menu/left-menu.component";

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [
    AsyncPipe,
    NgForOf,
    FormsModule,
    NgIf,
    CurrencyPipe,
    TopMenuComponent,
    LeftMenuComponent,
    KeyValuePipe,
    NgStyle
  ],
  templateUrl: './product-detail.component.html',
  styleUrls: ['./product-detail.component.css']
})
export class ProductDetailComponent implements OnInit, OnDestroy {
  product$ = new BehaviorSubject<Product | null>(null);
  quantity: number = 1;
  relatedProducts$ = new BehaviorSubject<Product[]>([]);
  selectedImageIndex: number = 0;
  imageUrls: { [index: number]: SafeUrl | null } = {};
  private subscriptions: Subscription[] = [];

  isZoomed: boolean = false;
  zoomFactor: number = 5;
  lensSize: number = 100;

  @ViewChild('imageContainer') imageContainer!: ElementRef<HTMLDivElement>;
  @ViewChild('zoomLens') zoomLens!: ElementRef<HTMLDivElement>;
  @ViewChild('mainImage') mainImage!: ElementRef<HTMLImageElement>;

  constructor(
    private route: ActivatedRoute,
    private productService: ProductService,
    private sanitizer: DomSanitizer,
    private renderer: Renderer2
  ) {}

  ngOnInit() {
    const productId = this.route.snapshot.paramMap.get('id');
    if (productId) {
      this.loadProduct(productId);
    }
  }

  ngOnDestroy() {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
  }

  private loadProduct(productId: string): void {
    const subscription = this.productService.getProduct(productId).subscribe({
      next: (product) => {
        this.product$.next(product);
        this.updateProductImages(product);
        this.loadRelatedProducts(product.categoryId);
      },
      error: (error) => console.error('Error loading product:', error)
    });
    this.subscriptions.push(subscription);
  }

  private updateProductImages(product: Product): void {
    product.images.forEach((imagePath, index) => {
      this.fetchImage(index, imagePath);
    });
  }

  private fetchImage(index: number, imagePath: string): void {
    const subscription = this.productService.getImage(imagePath).subscribe(blob => {
      const objectUrl = URL.createObjectURL(blob);
      this.imageUrls[index] = this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl);
      this.product$.next(this.product$.value);
    });
    this.subscriptions.push(subscription);
  }

  private loadRelatedProducts(categoryId: string): void {
    const subscription = this.productService.getProductsByCategory(categoryId).subscribe({
      next: (products: Product[]) => {
        this.relatedProducts$.next(products.filter(p => p.id !== this.product$.value?.id).slice(0, 4));
        this.updateRelatedProductImages(products);
      },
      error: (error) => console.error('Error loading related products:', error)
    });
    this.subscriptions.push(subscription);
  }

  private updateRelatedProductImages(products: Product[]): void {
    products.forEach(product => {
      if (product.images && product.images.length > 0) {
        this.fetchRelatedProductImage(product.id!, product.images[0]);
      }
    });
  }

  private fetchRelatedProductImage(productId: string, imagePath: string): void {
    const subscription = this.productService.getImage(imagePath).subscribe(blob => {
      const objectUrl = URL.createObjectURL(blob);
      const updatedProducts = this.relatedProducts$.value.map(p =>
        p.id === productId ? { ...p, imageUrl: this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl) } : p
      );
      this.relatedProducts$.next(updatedProducts);
    });
    this.subscriptions.push(subscription);
  }

  selectImage(index: number) {
    this.selectedImageIndex = index;
  }

  incrementQuantity(product: Product) {
    if (this.quantity < product.stockQuantity) {
      this.quantity++;
    }
  }

  decrementQuantity() {
    if (this.quantity > 1) {
      this.quantity--;
    }
  }

  addToCart(product: Product) {
    // Implement your add to cart logic here
    console.log('Adding to cart:', product, 'Quantity:', this.quantity);
  }

  toggleZoom(event: MouseEvent) {
    this.isZoomed = !this.isZoomed;
    if (this.isZoomed) {
      this.updateZoomPosition(event);
    }
  }

  updateZoomPosition(event: MouseEvent) {
    if (!this.isZoomed) return;

    const image = this.mainImage.nativeElement;
    const lens = this.zoomLens.nativeElement;
    const container = this.imageContainer.nativeElement;

    const rect = container.getBoundingClientRect();
    let x = event.clientX - rect.left;
    let y = event.clientY - rect.top;

    x = Math.max(0, Math.min(x, container.offsetWidth));
    y = Math.max(0, Math.min(y, container.offsetHeight));

    const lensLeft = x - this.lensSize / 2;
    const lensTop = y - this.lensSize / 2;

    this.renderer.setStyle(lens, 'left', `${lensLeft}px`);
    this.renderer.setStyle(lens, 'top', `${lensTop}px`);

    const zoomX = (x / container.offsetWidth) * 100;
    const zoomY = (y / container.offsetHeight) * 100;

    this.renderer.setStyle(image, 'transform-origin', `${zoomX}% ${zoomY}%`);
  }

  get transformScale(): string {
    return `scale(${this.zoomFactor})`;
  }
}
