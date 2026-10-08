import {Component, DestroyRef, ElementRef, inject, OnInit, Signal, signal, viewChild} from '@angular/core';
import {FormsModule, NgForm} from '@angular/forms';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {ProductService} from '../../../service/product.service';
import {EMPTY_PRODUCT_IMAGE} from '../../../service/image-loader.service';
import {Product} from '../../../models/product.model';
import {PagedList} from '../../../../shared/service/paged-list';
import {ButtonComponent} from '../../../../shared/components/button.component';
import {PageControlsComponent} from '../../../../shared/components/page-controls.component';
import {LeftMenuComponent} from '../left-menu/left-menu.component';
import {ProductCardComponent} from '../../../../shared/components/product-card/product-card.component';

@Component({selector: 'app-catalog', imports: [ProductCardComponent, RouterLink, FormsModule, PageControlsComponent,
  LeftMenuComponent, ButtonComponent], templateUrl: './catalog.component.html', styleUrl: './catalog.component.css'})
export class CatalogComponent implements OnInit {
  private readonly _products = inject(ProductService);
  private readonly _route = inject(ActivatedRoute);
  private readonly _router = inject(Router);
  private readonly _destroyed = inject(DestroyRef);
  private readonly $searchInput: Signal<ElementRef<HTMLInputElement> | undefined> = viewChild<ElementRef<HTMLInputElement>>('catalogSearch');
  private readonly $searchForm: Signal<NgForm | undefined> = viewChild<NgForm>('searchForm');
  readonly $items = signal<Product[]>([]);
  readonly $categoryId = signal<string | undefined>(undefined);
  readonly $query = signal('');
  readonly emptyImage: string = EMPTY_PRODUCT_IMAGE;
  search: string = '';
  readonly pages = new PagedList<Product>((page: number) =>
    this._products.getProductsPage(this.$categoryId(), page, 20, this.$query()),
    (items: Product[]): void => this.$items.set(items), this._destroyed);

  ngOnInit(): void {
    this._route.queryParamMap.pipe(takeUntilDestroyed(this._destroyed)).subscribe((params): void => {
      this.$categoryId.set(params.get('categoryId')?.trim() || undefined);
      this.$query.set(params.get('query')?.trim() || '');
      this.search = this.$query();
      this.$searchForm()?.form.markAsPristine();
      const page: number = Number(params.get('page') ?? 0);
      this.$items.set([]);
      this.pages.load(Number.isSafeInteger(page) && page >= 0 ? page : 0);
    });
  }

  changePage(page: number): void {
    if (page === this.pages.$page() && this.pages.$error()) { this.pages.load(page); return; }
    void this._router.navigate([], {relativeTo: this._route, queryParamsHandling: 'merge', queryParams: {page}});
  }

  searchProducts(focusSearch: boolean = false): void {
    this.search = this.search.trim();
    this.$searchForm()?.form.markAsPristine();
    void this._router.navigate([], {relativeTo: this._route, queryParamsHandling: 'merge',
      info: focusSearch ? 'catalog-search' : undefined,
      queryParams: {query: this.search.trim() || null, page: 0}});
  }

  clearSearch(): void {
    this.search = '';
    this.$searchInput()?.nativeElement.focus();
    this.searchProducts(true);
  }

  imageUrl(path: string): string { return this._products.imageUrl(path); }
}
