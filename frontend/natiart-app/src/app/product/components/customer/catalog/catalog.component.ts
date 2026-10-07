import {Component, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {CurrencyPipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {ProductService} from '../../../service/product.service';
import {Product} from '../../../models/product.model';
import {PagedList} from '../../../../shared/service/paged-list';
import {PageControlsComponent} from '../../../../shared/components/page-controls.component';
import {LeftMenuComponent} from '../left-menu/left-menu.component';
import {ButtonComponent} from '../../../../shared/components/button.component';
import {TopMenuComponent} from '../top-menu/top-menu.component';

@Component({selector: 'app-catalog', imports: [ButtonComponent, CurrencyPipe, FormsModule, RouterLink, PageControlsComponent,
  LeftMenuComponent, TopMenuComponent], templateUrl: './catalog.component.html'})
export class CatalogComponent implements OnInit {
  private readonly _products = inject(ProductService);
  private readonly _route = inject(ActivatedRoute);
  private readonly _router = inject(Router);
  private readonly _destroyed = inject(DestroyRef);
  readonly $items = signal<Product[]>([]);
  readonly $categoryId = signal<string | undefined>(undefined);
  readonly $query = signal('');
  search: string = '';
  readonly pages = new PagedList<Product>((page: number) =>
    this._products.getProductsPage(this.$categoryId(), page, 20, this.$query()),
    (items: Product[]): void => this.$items.set(items), this._destroyed);

  ngOnInit(): void {
    this._route.queryParamMap.pipe(takeUntilDestroyed(this._destroyed)).subscribe((params): void => {
      this.$categoryId.set(params.get('categoryId')?.trim() || undefined);
      this.$query.set(params.get('query')?.trim() || '');
      this.search = this.$query();
      const page: number = Number(params.get('page') ?? 0);
      this.$items.set([]);
      this.pages.load(Number.isSafeInteger(page) && page >= 0 ? page : 0);
    });
  }

  changePage(page: number): void {
    if (page === this.pages.$page() && this.pages.$error()) { this.pages.load(page); return; }
    void this._router.navigate([], {relativeTo: this._route, queryParamsHandling: 'merge', queryParams: {page}});
  }

  searchProducts(): void {
    void this._router.navigate([], {relativeTo: this._route, queryParamsHandling: 'merge',
      queryParams: {query: this.search.trim() || null, page: 0}});
  }

  imageUrl(path: string): string { return this._products.imageUrl(path); }
}
